package edu.course.shop.service;

import edu.course.shop.Services;
import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Admin-only orchestration: interpret -> local retrieval -> reviewed proposal -> atomic apply. */
public final class AiManagerService {
    public record Metrics(int products,int needContent,int lowStock,int inactiveWithStock) { }
    public record Sales(String period,int orders,BigDecimal orderValue,BigDecimal paidValue,int units) { }
    public record Result(String batchId,ManagerCommand command,List<Product> products,List<ProductService.ManagerChange> changes,
            List<ManagerAi.Draft> drafts,Sales sales,boolean offline,String message,int totalMatches,String aiIssue) {
        public Result {products=List.copyOf(products);changes=List.copyOf(changes);drafts=List.copyOf(drafts);}
        public Result(String batchId,ManagerCommand command,List<Product> products,List<ProductService.ManagerChange> changes,
                List<ManagerAi.Draft> drafts,Sales sales,boolean offline,String message,int totalMatches){
            this(batchId,command,products,changes,drafts,sales,offline,message,totalMatches,"");
        }
    }
    private final Services services;private final ManagerAi ai;private final Clock clock;
    public AiManagerService(Services services,GeminiAssistant gemini){this(services,new ManagerAi(gemini),Clock.systemDefaultZone());}
    AiManagerService(Services services,ManagerAi ai,Clock clock){this.services=services;this.ai=ai;this.clock=clock;}
    public boolean configured(){return ai.configured();}
    private static void requireAdmin(User actor){if(actor==null||!actor.canManageProducts())throw new ValidationException("Administrator access is required.");}
    public Metrics metrics(User actor){requireAdmin(actor);List<Product> p=services.products().search(actor,"",null);return new Metrics(p.size(),(int)p.stream().filter(ManagerCommand::needsContent).count(),(int)p.stream().filter(x->x.isActive()&&x.getStock()<=5).count(),(int)p.stream().filter(x->!x.isActive()&&x.getStock()>0).count());}
    public Result prepare(User actor,String request,boolean research){return prepare(actor,request,research,stage->{});}
    public Result prepare(User actor,String request,boolean research,java.util.function.Consumer<String> progress){
        requireAdmin(actor);if(request==null||request.isBlank()||request.length()>1000)throw new ValidationException("Enter a store command of 1–1,000 characters.");
        progress.accept("Interpreting your command…");
        boolean offline=!ai.configured();String aiIssue=offline?ai.configurationProblem():"";ManagerCommand command;
        if(offline)command=ManagerCommand.offline(request);
        else try{command=ai.classify(request);}catch(IllegalStateException e){if(Thread.currentThread().isInterrupted())throw e;offline=true;aiIssue="Command interpretation: "+e.getMessage();command=ManagerCommand.offline(request);}
        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Command cancelled.");
        if(command.intent()==ManagerCommand.Intent.CLARIFY)return result(command,List.of(),List.of(),List.of(),null,offline,command.question(),0,aiIssue);
        progress.accept("Checking catalog records…");List<Product> catalog=services.products().search(actor,"",null);
        if(command.intent()==ManagerCommand.Intent.SALES_SUMMARY){
            progress.accept("Calculating recorded sales totals…");Sales sales=sales(actor,command);List<Product> restock=command.includeRestock()?catalog.stream().filter(p->p.isActive()&&p.getStock()<=5).sorted(Comparator.comparingInt(Product::getStock)).toList():List.of();
            return result(command,restock,List.of(),List.of(),sales,offline,"Recorded order totals are calculated from SQLite. Payments are simulated; pending payments are excluded from paid value.",restock.size(),aiIssue);
        }
        List<Product> matched=catalog.stream().filter(command::matches).sorted(Comparator.comparingInt(Product::getStock).thenComparing(Product::getName)).toList();
        if(command.intent()==ManagerCommand.Intent.QUERY_PRODUCTS||command.intent()==ManagerCommand.Intent.FIND_LOW_STOCK)
            return result(command,matched,List.of(),List.of(),null,offline,matched.isEmpty()?"No products match these filters. Try a different category, price or stock level.":"Found "+matched.size()+" matching products.",matched.size(),aiIssue);
        int count=matched.size();List<Product> batch=matched.stream().limit(20).toList();
        if(command.intent()==ManagerCommand.Intent.UPDATE_STATUS){
            boolean target=command.targetActive();
            List<ProductService.ManagerChange> changes=batch.stream().filter(p->p.isActive()!=target)
                .map(p->new ProductService.ManagerChange(p,null,null,target,"")).toList();
            return result(command,batch,changes,List.of(),null,offline,changes.isEmpty()?"These products already have the requested status.":"Review "+changes.size()+" status changes before applying."+(count>20?" This preview contains the first 20 matches; narrow your filters for a smaller selection.":""),count,aiIssue);
        }
        // Prioritize fields this command can actually fill before applying the batch limit.
        // Photo-only records and specs without research must not spend a generation request
        // or prevent later products with missing descriptions from being reached.
        String missingField=command.missingField();
        List<Product> draftable=matched.stream().filter(p->
            !missingField.equals("photo")&&(!missingField.equals("specifications")&&p.getDescription().isBlank()
                ||research&&!missingField.equals("description")&&p.getSpecifications().isBlank())).toList();
        if(!offline&&!draftable.isEmpty())batch=draftable.stream().limit(20).toList();
        List<ManagerAi.Draft> drafts=List.of();String message;
        if(batch.isEmpty())message="No products with missing content match this request.";
        else if(command.missingField().equals("photo"))message="These products need a photo upload in Products. AI Manager does not fabricate product photos.";
        else if(offline)message="Found "+count+" products needing content. AI drafting is offline; you can inspect these records and retry when connected.";
        else if(draftable.isEmpty())message="These products need a photo upload or researched specifications. Upload missing photos in Products; enable Research missing specifications online for exact-model lookup. No drafting request was made.";
        else {
            progress.accept("Drafting content for "+batch.size()+" products"+(research?" with source research":"")+"…");
            try{drafts=ai.draft(batch,research);message="Review the proposed content. Existing fields are preserved; missing photos and unverified specifications still need attention.";}
            catch(IllegalStateException e){if(Thread.currentThread().isInterrupted())throw e;offline=true;aiIssue="Content drafting: "+e.getMessage();message="Content drafting is unavailable right now. Your matching products are below; nothing was changed.";}
        }
        Map<Long,Product> byId=new HashMap<>();batch.forEach(p->byId.put(p.getId(),p));List<ProductService.ManagerChange> changes=new ArrayList<>();
        for(ManagerAi.Draft draft:drafts){Product p=byId.get(draft.id());
            String description=!command.missingField().equals("specifications")&&p.getDescription().isBlank()&&!draft.description().isBlank()?draft.description():null;
            String specs=!command.missingField().equals("description")&&p.getSpecifications().isBlank()&&!draft.specifications().isBlank()?draft.specifications():null;
            if(description!=null||specs!=null)changes.add(new ProductService.ManagerChange(p,description,specs,null,String.join("\n",draft.sources())));
        }
        if(count>batch.size())message+=" Showing "+batch.size()+" of "+count+" matching products. Photo-only records or specifications without research may require separate attention; narrower filters can select a smaller group.";
        return result(command,batch,changes,drafts,null,offline,message,count,aiIssue);
    }
    private Result result(ManagerCommand c,List<Product> p,List<ProductService.ManagerChange> changes,List<ManagerAi.Draft> drafts,Sales sales,boolean offline,String message,int count,String aiIssue){return new Result(UUID.randomUUID().toString(),c,p,changes,drafts,sales,offline,message,count,aiIssue);}
    private Sales sales(User actor,ManagerCommand command){
        LocalDate now=LocalDate.now(clock);LocalDate start=switch(command.period()){
            case "today"->now;case "this_week"->now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));case "this_month"->now.withDayOfMonth(1);default->now.minusDays(command.days()-1L);};
        Instant from=start.atStartOfDay(clock.getZone()).toInstant(),until=now.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        var rows=services.admin().orders(actor).stream().filter(o->{try{Instant time=Instant.parse(o.createdAt());return !time.isBefore(from)&&time.isBefore(until);}catch(DateTimeException e){return false;}}).toList();
        long total=0,paid=0;int units=0;for(var row:rows){total=Math.addExact(total,row.totalCents());units=Math.addExact(units,row.itemCount());if("PAID".equalsIgnoreCase(row.paymentStatus()))paid=Math.addExact(paid,row.totalCents());}
        return new Sales(start+" — "+now,rows.size(),Money.amount(total),Money.amount(paid),units);
    }
    public void apply(User actor,Result result){requireAdmin(actor);services.products().applyManagerChanges(actor,result.batchId(),result.command().intent().name().toLowerCase(Locale.ROOT),result.changes());}
}
