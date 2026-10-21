package edu.course.shop.service;

import com.google.gson.*;
import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Bounded tool arguments. The model cannot provide SQL, field names or executable code. */
public record ManagerCommand(Intent intent, String query, Category category, BigDecimal belowPrice,
        Integer stockAtMost, Integer stockAtLeast, String status, Boolean targetActive,
        boolean missingContent, String missingField, List<Long> ids, String period, int days, boolean includeRestock, String question) {
    public enum Intent { FIX_CONTENT, FIND_LOW_STOCK, QUERY_PRODUCTS, SALES_SUMMARY, UPDATE_STATUS, CLARIFY }
    public ManagerCommand {
        Objects.requireNonNull(intent);query=query==null?"":query.trim();ids=List.copyOf(ids);
        if(query.length()>100||ids.size()>20||ids.stream().anyMatch(id->id<=0))throw new ValidationException("Narrow the product selection.");
        if(belowPrice!=null&&(belowPrice.signum()<=0||belowPrice.scale()>2))throw new ValidationException("Enter a positive price with at most two decimals.");
        if(stockAtMost!=null&&(stockAtMost<0||stockAtMost>100000)||stockAtLeast!=null&&(stockAtLeast<0||stockAtLeast>100000))throw new ValidationException("Stock filters must be between 0 and 100,000.");
        if(stockAtMost!=null&&stockAtLeast!=null&&stockAtLeast>stockAtMost)throw new ValidationException("The stock range is contradictory.");
        if(!Set.of("any","active","inactive").contains(status)||!Set.of("last_days","today","this_week","this_month").contains(period)||days<1||days>366)throw new ValidationException("The command contains an unsupported filter.");
        if(!Set.of("any","description","specifications","photo").contains(missingField))throw new ValidationException("Choose which missing content to find.");
        if(intent==Intent.UPDATE_STATUS&&targetActive==null)throw new ValidationException("Should the selected products become active or inactive?");
    }
    public boolean matches(Product p) {
        return (query.isBlank()||p.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            &&(category==null||category==p.getCategory())&&(belowPrice==null||p.getPrice().compareTo(belowPrice)<0)
            &&(stockAtMost==null||p.getStock()<=stockAtMost)&&(stockAtLeast==null||p.getStock()>=stockAtLeast)
            &&(status.equals("any")||p.isActive()==status.equals("active"))&&(ids.isEmpty()||ids.contains(p.getId()))
            &&(!missingContent||switch(missingField){case "description"->p.getDescription().isBlank();case "specifications"->p.getSpecifications().isBlank();case "photo"->p.getPhoto()==null;default->needsContent(p);});
    }
    public static boolean needsContent(Product p){return p.getPhoto()==null||p.getDescription().isBlank()||p.getSpecifications().isBlank();}
    static ManagerCommand parse(String tool,JsonObject args) {
        try {
            Intent intent=Intent.valueOf(tool.toUpperCase(Locale.ROOT));
            if(args==null||!arguments(intent).containsAll(args.keySet()))throw new ValidationException("FORGE AI returned an unsupported command argument. Please rephrase.");
            Category category=has(args,"category")&&!args.get("category").getAsString().isBlank()?Category.valueOf(args.get("category").getAsString()):null;
            List<Long> ids=new ArrayList<>();if(has(args,"ids"))for(JsonElement id:args.getAsJsonArray("ids"))ids.add(id.getAsBigDecimal().longValueExact());
            Integer maxStock=has(args,"stockAtMost")?Integer.valueOf(args.get("stockAtMost").getAsBigDecimal().intValueExact()):intent==Intent.FIND_LOW_STOCK?Integer.valueOf(5):null;
            return new ManagerCommand(intent,string(args,"query",""),category,has(args,"belowPrice")?args.get("belowPrice").getAsBigDecimal():null,
                maxStock,
                has(args,"stockAtLeast")?args.get("stockAtLeast").getAsBigDecimal().intValueExact():null,
                string(args,"status",intent==Intent.FIND_LOW_STOCK?"active":"any"),has(args,"targetActive")?bool(args,"targetActive"):null,
                intent==Intent.FIX_CONTENT||has(args,"missingContent")&&bool(args,"missingContent")||!string(args,"missingField","any").equals("any"),string(args,"missingField","any"),ids,
                string(args,"period","last_days"),has(args,"days")?args.get("days").getAsBigDecimal().intValueExact():7,
                has(args,"includeRestock")&&bool(args,"includeRestock"),string(args,"question","Which store task would you like help with?"));
        }catch(ValidationException e){throw e;}catch(RuntimeException e){throw new ValidationException("FORGE AI could not interpret that command safely. Please rephrase.");}
    }
    static Set<String> arguments(Intent intent){
        if(intent==Intent.CLARIFY)return Set.of("question");
        if(intent==Intent.SALES_SUMMARY)return Set.of("period","days","includeRestock");
        Set<String> fields=new HashSet<>(Set.of("query","category","belowPrice","stockAtMost","stockAtLeast","status","ids"));
        if(intent==Intent.UPDATE_STATUS)fields.add("targetActive");
        if(intent==Intent.FIX_CONTENT||intent==Intent.QUERY_PRODUCTS){fields.add("missingContent");fields.add("missingField");}
        return fields;
    }
    private static boolean has(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull();}
    private static String string(JsonObject o,String k,String fallback){return has(o,k)?o.get(k).getAsString():fallback;}
    private static boolean bool(JsonObject o,String k){if(!o.get(k).isJsonPrimitive()||!o.getAsJsonPrimitive(k).isBoolean())throw new ValidationException("Expected a true/false tool argument.");return o.get(k).getAsBoolean();}

    /** Offline intent inference uses token similarity and semantic synonym groups, not exact sentences. */
    static ManagerCommand offline(String request) {
        String text=request.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9$.,# ]"," ").replaceAll("\\s+"," ").trim();
        Set<String> tokens=new HashSet<>(List.of(text.split("[^a-z0-9]+")));
        if(similarity(tokens,"delete erase purge destroy remove refund")>=.99)return clarify("I can draft missing content, query inventory, report sales and preview active/inactive changes. Product deletion and refunds aren't supported.");
        double content=similarity(tokens,"content description descriptions details specifications specs copy writing information");
        double repair=similarity(tokens,"fix fill complete missing incomplete empty blank absent improve generate write enrich lacking");
        if(content>=.75&&(tokens.contains("no")||tokens.contains("without")))repair=1;
        double low=similarity(tokens,"low shortage scarce depleted restock replenish running dwindling limited");
        double stock=similarity(tokens,"stock inventory quantities units remaining");
        double sales=similarity(tokens,"sales revenue earnings turnover income sold made make money performance");
        double change=similarity(tokens,"deactivate activate disable enable hide unpublish publish remove make mark switch set");
        double off=similarity(tokens,"inactive disabled hidden unavailable deactivate unpublish hide");
        double on=similarity(tokens,"active enabled available activate publish enable");
        double deactivate=similarity(tokens,"deactivate disable hide unpublish"),activate=similarity(tokens,"activate enable publish");
        Intent intent=change>=.8&&(off>=.8||on>=.8)?Intent.UPDATE_STATUS:
            content>=.75&&repair>=.75?Intent.FIX_CONTENT:sales>=.8?Intent.SALES_SUMMARY:
            low>=.8&&(stock>=.7||similarity(tokens,"restock replenish")>=.8)?Intent.FIND_LOW_STOCK:Intent.QUERY_PRODUCTS;
        if(intent==Intent.QUERY_PRODUCTS&&similarity(tokens,"products product inventory items laptops parts components inactive active show find list search")<.8)
            return parse("clarify",JsonParser.parseString("{\"question\":\"Try asking for missing content, low stock, products, sales, or a status change.\"}").getAsJsonObject());
        JsonObject args=new JsonObject();
        if(intent==Intent.FIX_CONTENT){
            boolean description=similarity(tokens,"description descriptions")>=.8,specs=similarity(tokens,"specifications specs")>=.8;
            if(description!=specs)args.addProperty("missingField",description?"description":"specifications");
        }
        if(intent==Intent.UPDATE_STATUS||intent==Intent.FIX_CONTENT){
            String vocabulary="content description descriptions details specifications specs copy writing information fix fill complete missing incomplete empty blank absent improve generate write enrich lacking low shortage scarce depleted restock replenish running dwindling limited stock inventory quantities units remaining deactivate activate disable enable hide unpublish publish remove make mark switch set inactive active disabled enabled hidden unavailable available products product items laptops parts components computers pc sets tablets phones accessories peripherals electronics gpu gpus cpu ram ssd motherboard psu keyboard mouse headset notebook desktop all every everything under below less than cheaper price today week month last past sales summary";
            Set<String> stop=Set.of("a","an","the","i","me","my","we","our","you","please","can","could","would","should","want","need","needs","with","without","no","and","or","for","from","in","on","to","of","that","are","is","has","have","some","store","shop","this","it","these","those","up","at","most","units");
            if(tokens.stream().anyMatch(token->!token.isBlank()&&!token.matches("\\d+")&&!stop.contains(token)&&similarity(Set.of(token),vocabulary)<.75))
                return clarify("Use a category or product #ID, plus price or stock filters for a precise offline selection.");
        }
        Category c=CatalogAssistant.requestedCategory(text);if(c!=null)args.addProperty("category",c.name());
        Matcher price=Pattern.compile("(?:under|below|less than|cheaper than)\\s*\\$?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)").matcher(text);
        if(price.find())args.addProperty("belowPrice",new BigDecimal(price.group(1).replace(",","")));
        else if(text.contains("$")||text.matches(".*\\b(price|cost|priced)\\b.*\\d.*"))return clarify("Please express the price filter as ‘under $20’ while offline.");
        if(low>=.8&&stock>=.7)args.addProperty("stockAtMost",5);
        Matcher units=Pattern.compile("(?:stock (?:under|below)|fewer than|less than)\\s*(\\d+)\\s*(?:units|items)?").matcher(text);
        if(units.find())args.addProperty("stockAtMost",Math.max(0,Integer.parseInt(units.group(1))-1));
        if(text.matches(".*\\b(with|has|have) (?:some )?stock\\b.*"))args.addProperty("stockAtLeast",1);
        if(intent==Intent.UPDATE_STATUS){
            if(text.matches(".*\\b(not|except|unless|instead)\\b.*"))return clarify("Please state the products and desired active/inactive status without exclusions while offline.");
            if(Math.max(deactivate,activate)<.9&&Math.abs(on-off)<.1)return clarify("Should the selected products become active or inactive?");
            boolean target=Math.max(deactivate,activate)>=.9?activate>deactivate:on>off;
            args.addProperty("targetActive",target);
            if(deactivate>=.99&&tokens.contains("active"))args.addProperty("status","active");
            if(activate>=.99&&tokens.contains("inactive"))args.addProperty("status","inactive");
        }else if(off>=.9)args.addProperty("status","inactive");
        if(intent==Intent.SALES_SUMMARY){
            if(c!=null||args.has("belowPrice"))return clarify("Sales summaries currently cover the whole store. Ask for a time period and optionally restock attention.");
            args.addProperty("period",text.contains("today")?"today":text.contains("this week")?"this_week":text.contains("this month")?"this_month":"last_days");
            Matcher days=Pattern.compile("(?:last|past) (\\d+) days").matcher(text);if(days.find())args.addProperty("days",Integer.parseInt(days.group(1)));
            args.addProperty("includeRestock",low>=.8);
        }
        Matcher id=Pattern.compile("#(\\d+)").matcher(text);JsonArray ids=new JsonArray();while(id.find())ids.add(Long.parseLong(id.group(1)));if(!ids.isEmpty())args.add("ids",ids);
        // Offline bulk writes need an explicit, understood selection; never broaden an unknown product name.
        if(intent==Intent.UPDATE_STATUS&&c==null&&!args.has("belowPrice")&&!args.has("stockAtMost")&&!args.has("stockAtLeast")&&!args.has("status")&&!args.has("ids")&&!text.matches(".*\\b(all|every|everything)\\b.*"))
            return clarify("Which products should change? Use a category, product #ID, or a price and stock filter while offline.");
        if(intent==Intent.QUERY_PRODUCTS&&c==null&&!args.has("status")&&!args.has("ids")&&!args.has("belowPrice")&&!text.matches(".*\\b(all|every|inventory)\\b.*"))
            return clarify("Use a category or product #ID for a precise offline search.");
        args.keySet().retainAll(arguments(intent));
        return parse(intent.name().toLowerCase(Locale.ROOT),args);
    }
    private static ManagerCommand clarify(String text){JsonObject a=new JsonObject();a.addProperty("question",text);return parse("clarify",a);}
    private static double similarity(Set<String> tokens,String concepts) {
        double best=0;for(String token:tokens)for(String concept:concepts.split(" ")){
            if(token.equals(concept))return 1;
            if(token.length()<4||concept.length()<4)continue;
            int[] prev=new int[concept.length()+1],before=null;for(int i=0;i<prev.length;i++)prev[i]=i;
            for(int i=1;i<=token.length();i++){
                int[] row=new int[prev.length];row[0]=i;
                for(int j=1;j<row.length;j++){
                    row[j]=Math.min(Math.min(row[j-1]+1,prev[j]+1),prev[j-1]+(token.charAt(i-1)==concept.charAt(j-1)?0:1));
                    if(i>1&&j>1&&token.charAt(i-1)==concept.charAt(j-2)&&token.charAt(i-2)==concept.charAt(j-1))row[j]=Math.min(row[j],before[j-2]+1);
                }before=prev;prev=row;
            }
            best=Math.max(best,1.0-(double)prev[concept.length()]/Math.max(token.length(),concept.length()));
        }return best;
    }
}
