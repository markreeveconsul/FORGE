package edu.course.shop.service;

import com.google.gson.*;
import edu.course.shop.Services;
import edu.course.shop.exception.ValidationException;
import edu.course.shop.model.*;
import edu.course.shop.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class AiManagerServiceTest {
    @TempDir Path temp;
    DatabaseManager db;Services services;User admin;Product gpu,boundary,abundant,laptop;
    @BeforeEach void setup()throws Exception{
        db=new DatabaseManager(temp.resolve("manager.db"));db.initialize();var pr=new ProductRepository();var or=new OrderRepository();
        services=new Services(new AuthService(db,new UserRepository()),new ProductService(db,pr),new CheckoutService(db,pr,or),new OrderService(db,or),new AdminService(db,new AdminRepository()),new ProductReviewService(db,new ProductReviewRepository()));
        admin=services.auth().login("admin","admin123");
        try(var c=db.open();var s=c.createStatement()){s.executeUpdate("DELETE FROM products");}
        gpu=add("Aurora GPU",Category.PC_PARTS,"19.99",2,true);boundary=add("Boundary GPU",Category.PC_PARTS,"20",2,true);
        abundant=add("Abundant GPU",Category.PC_PARTS,"10",8,true);laptop=add("Hidden Laptop",Category.LAPTOPS,"900",4,false);
    }
    Product add(String name,Category category,String price,int stock,boolean active){services.products().save(admin,0,name,category,new BigDecimal(price),stock,active);return services.products().search(admin,name,null).getFirst();}
    GeminiAssistant client(GeminiAssistant.Sender sender){return new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-test",true),sender);}
    AiManagerService offline(){return new AiManagerService(services,new GeminiAssistant(new GeminiAssistant.Config("",null,true),r->{throw new AssertionError("Offline mode must not call Google");}));}
    static GeminiAssistant.Reply call(String name,String args){JsonObject fc=new JsonObject();fc.addProperty("name",name);fc.add("args",JsonParser.parseString(args));return response(new Gson().toJsonTree(List.of(Map.of("functionCall",fc))).getAsJsonArray(),null);}
    static GeminiAssistant.Reply response(JsonArray parts,JsonObject grounding){JsonObject c=new JsonObject();c.addProperty("finishReason","STOP");c.add("content",new Gson().toJsonTree(Map.of("parts",parts)));if(grounding!=null)c.add("groundingMetadata",grounding);return new GeminiAssistant.Reply(200,new Gson().toJson(Map.of("candidates",List.of(c))));}
    static GeminiAssistant.Reply draft(long id,String desc,String specs,String source,boolean grounded){
        var row=Map.of("id",id,"description",desc,"specifications",specs,"exactModel",true,"sources",source.isBlank()?List.of():List.of(source),"note","Check exact model.");
        String json=new Gson().toJson(Map.of("drafts",List.of(row)));JsonArray parts=new Gson().toJsonTree(List.of(Map.of("text",json,"thought",false))).getAsJsonArray();
        return response(parts,grounded?new Gson().toJsonTree(Map.of("groundingChunks",List.of(Map.of("web",Map.of("uri",source))))).getAsJsonObject():null);
    }
    int auditCount()throws Exception{try(var c=db.open();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM admin_ai_changes")){r.next();return r.getInt(1);}}
    static String requestBody(java.net.http.HttpRequest request){
        var result=new CompletableFuture<String>();var bytes=new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer b){byte[] chunk=new byte[b.remaining()];b.get(chunk);bytes.writeBytes(chunk);}
            public void onError(Throwable e){result.completeExceptionally(e);}
            public void onComplete(){result.complete(bytes.toString(StandardCharsets.UTF_8));}
        });return result.join();
    }
    @Test void guestAndCustomerCannotReadPlanOrApplyAdminCommands(){
        var manager=offline();User customer=services.auth().login("customer","customer123");
        for(User actor:List.of(new Guest(),customer)){
            assertThrows(ValidationException.class,()->manager.metrics(actor));
            assertThrows(ValidationException.class,()->manager.prepare(actor,"show inventory",false));
            assertThrows(ValidationException.class,()->services.products().applyManagerChanges(actor,UUID.randomUUID().toString(),"update_status",List.of()));
        }
    }
    @Test void offlineFallbackUnderstandsParaphrasesAndTyposWithoutExactSentences(){
        for(String command:List.of("fix missing content","fill in product details","products with no description","Please fix missing contnet"))
            assertEquals(ManagerCommand.Intent.FIX_CONTENT,ManagerCommand.offline(command).intent(),command);
        assertEquals(ManagerCommand.Intent.FIND_LOW_STOCK,ManagerCommand.offline("Show low stok").intent());
        var inactive=offline().prepare(admin,"Find inactive products with stock",false);
        assertTrue(inactive.offline());assertEquals(List.of(laptop.getId()),inactive.products().stream().map(Product::getId).toList());
        assertTrue(inactive.changes().isEmpty());
        assertEquals(ManagerCommand.Intent.CLARIFY,ManagerCommand.offline("deactivate all Aurora products").intent());
        assertEquals(ManagerCommand.Intent.CLARIFY,ManagerCommand.offline("show Aurora products").intent());
        var summary=ManagerCommand.offline("Show this week's sales summary and restock attention");
        assertEquals(ManagerCommand.Intent.SALES_SUMMARY,summary.intent());assertTrue(summary.includeRestock());
        assertEquals(ManagerCommand.Intent.CLARIFY,ManagerCommand.offline("delete all products").intent());
    }
    @Test void classificationUsesRealFunctionDeclarationsAndIgnoresKeywordWording(){
        AtomicInteger calls=new AtomicInteger();
        var manager=new AiManagerService(services,client(request->{calls.incrementAndGet();
            JsonObject body=JsonParser.parseString(requestBody(request)).getAsJsonObject();
            assertEquals(6,body.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonArray("functionDeclarations").size());
            assertEquals("ANY",body.getAsJsonObject("toolConfig").getAsJsonObject("functionCallingConfig").get("mode").getAsString());
            assertEquals("generateContent",request.uri().getPath().substring(request.uri().getPath().lastIndexOf(':')+1));
            return call("query_products","{\"category\":\"LAPTOPS\",\"status\":\"inactive\",\"stockAtLeast\":1}");
        }));
        var result=manager.prepare(admin,"Which sleeping notebook listings could go back on the shelf?",false);
        assertFalse(result.offline());assertEquals(1,calls.get());assertEquals(List.of(laptop.getId()),result.products().stream().map(Product::getId).toList());
        assertTrue(result.changes().isEmpty());
    }
    @Test void missingDescriptionRequestDoesNotSelectProductsThatOnlyLackOtherFields(){
        services.products().save(admin,gpu.getId(),gpu.getName(),gpu.getCategory(),gpu.getPrice(),gpu.getStock(),true,"Existing description",null,List.of(),"");
        var result=offline().prepare(admin,"products with no description",false);
        assertEquals("description",result.command().missingField());assertEquals(3,result.products().size());
        assertTrue(result.products().stream().noneMatch(p->p.getId()==gpu.getId()));
    }
    @Test void offlineStatusPreviewHonorsPriceStockAndDirectionThenAppliesOnce()throws Exception{
        var manager=offline();var preview=manager.prepare(admin,"Deactivate all active products under $20 with low stock",false);
        assertEquals(ManagerCommand.Intent.UPDATE_STATUS,preview.command().intent());assertFalse(preview.command().targetActive());
        assertEquals(List.of(gpu.getId()),preview.changes().stream().map(c->c.expected().getId()).toList());
        assertTrue(services.products().get(gpu.getId()).isActive());
        manager.apply(admin,preview);
        assertFalse(services.products().get(gpu.getId()).isActive());assertTrue(services.products().get(boundary.getId()).isActive());assertTrue(services.products().get(abundant.getId()).isActive());
        assertEquals(1,auditCount());assertThrows(ValidationException.class,()->manager.apply(admin,preview));assertEquals(1,auditCount());
        var activation=manager.prepare(admin,"Activate inactive products with stock",false);assertTrue(activation.command().targetActive());
        assertTrue(activation.products().stream().noneMatch(Product::isActive));
    }
    @Test void staleRecordRollsBackTheWholeBatchAndAudit()throws Exception{
        var preview=offline().prepare(admin,"Deactivate all active PC parts",false);
        assertEquals(3,preview.changes().size());
        Product stale=preview.changes().getLast().expected();services.products().save(admin,stale.getId(),stale.getName(),stale.getCategory(),stale.getPrice(),stale.getStock()+1,true);
        assertThrows(ValidationException.class,()->offline().apply(admin,preview));
        for(var change:preview.changes())assertTrue(services.products().get(change.expected().getId()).isActive());
        assertEquals(0,auditCount());
    }
    @Test void contentIsOnlyADraftUntilApprovedAndDoesNotPretendPhotosWereFilled()throws Exception{
        AtomicInteger calls=new AtomicInteger();var manager=new AiManagerService(services,client(r->calls.getAndIncrement()==0
            ?call("fix_content","{\"ids\":["+gpu.getId()+"]}")
            :draft(gpu.getId(),"Aurora GPU from the PC parts collection.","Memory: 99 GB","https://manufacturer.example/specs",false)));
        var preview=manager.prepare(admin,"Please complete this listing",true);
        assertEquals(2,calls.get());assertEquals(1,preview.changes().size());assertNull(preview.changes().getFirst().specifications());
        assertTrue(services.products().get(gpu.getId()).getDescription().isBlank());
        manager.apply(admin,preview);Product saved=services.products().get(gpu.getId());
        assertEquals("Aurora GPU from the PC parts collection.",saved.getDescription());assertTrue(saved.getSpecifications().isBlank());assertNull(saved.getPhoto());
        assertEquals(gpu.getPrice(),saved.getPrice());assertEquals(gpu.getStock(),saved.getStock());assertTrue(saved.isActive());
        assertTrue(ManagerCommand.needsContent(saved));assertEquals(1,auditCount());
    }
    @Test void retrievedSpecificationSourcesArePreservedAndExistingDescriptionIsKept(){
        services.products().save(admin,gpu.getId(),gpu.getName(),gpu.getCategory(),gpu.getPrice(),gpu.getStock(),true,"Admin-written copy",null,List.of(),"");
        AtomicInteger calls=new AtomicInteger();String source="https://manufacturer.example/aurora/exact-model";
        var manager=new AiManagerService(services,client(r->calls.getAndIncrement()==0?call("fix_content","{\"ids\":["+gpu.getId()+"]}"):draft(gpu.getId(),"Replacement should be ignored","Memory: 8 GB",source,true)));
        var preview=manager.prepare(admin,"Fill in the product information",true);
        assertNull(preview.changes().getFirst().description());assertEquals("Memory: 8 GB",preview.changes().getFirst().specifications());assertEquals(source,preview.changes().getFirst().sources());
        manager.apply(admin,preview);assertEquals("Admin-written copy",services.products().get(gpu.getId()).getDescription());
    }
    @Test void providerOutageFallsBackToLocalRetrievalButDoesNotFabricateDrafts(){
        var manager=new AiManagerService(services,client(r->new GeminiAssistant.Reply(503,"{}")));
        var read=manager.prepare(admin,"Show low stock",false);assertTrue(read.offline());assertEquals(2,read.products().size());
        var content=manager.prepare(admin,"Fix missing content",false);assertTrue(content.offline());assertTrue(content.changes().isEmpty());assertTrue(content.message().contains("offline"));
        assertTrue(content.aiIssue().contains("Command interpretation: HTTP 503"));
    }
    @Test void rejectedRequestKeepsSafeProviderDetailsInsteadOfSilentlyGoingOffline(){
        String key="private-test-key";
        var gemini=new GeminiAssistant(new GeminiAssistant.Config(key,"gemini-test",true),r->new GeminiAssistant.Reply(400,
            new Gson().toJson(Map.of("error",Map.of("message","Unsupported setting. key="+key+"\nCheck generationConfig.")))));
        var preview=new AiManagerService(services,gemini).prepare(admin,"Fix missing content",false);
        assertTrue(preview.offline());assertTrue(preview.changes().isEmpty());
        assertTrue(preview.aiIssue().contains("HTTP 400"));assertTrue(preview.aiIssue().contains("Unsupported setting"));
        assertFalse(preview.aiIssue().contains(key));assertFalse(preview.aiIssue().contains("\n"));
    }
    @Test void draftStageFailureIsDistinctAndDoesNotPermanentlyDisableTheConnection(){
        AtomicInteger requests=new AtomicInteger();
        var manager=new AiManagerService(services,client(r->switch(requests.getAndIncrement()){
            case 0,2->call("fix_content","{\"ids\":["+gpu.getId()+"]}");
            case 1->new GeminiAssistant.Reply(503,"{}");
            default->draft(gpu.getId(),"A graphics card from the catalog.","","",false);
        }));
        var failed=manager.prepare(admin,"Fix missing content",false);
        assertTrue(failed.offline());assertTrue(failed.aiIssue().startsWith("Content drafting: HTTP 503"));
        assertTrue(failed.changes().isEmpty());assertTrue(services.products().get(gpu.getId()).getDescription().isBlank());
        var retry=manager.prepare(admin,"Fix missing content",false);
        assertFalse(retry.offline());assertTrue(retry.aiIssue().isBlank());assertEquals(1,retry.changes().size());
    }
    @Test void tokenExhaustionIsReportedWithoutApplyingTruncatedTools(){
        var manager=new AiManagerService(services,client(r->new GeminiAssistant.Reply(200,
            call("update_status","{\"targetActive\":false}").body().replace("STOP","MAX_TOKENS"))));
        var preview=manager.prepare(admin,"Fix missing content",false);
        assertTrue(preview.offline());assertTrue(preview.aiIssue().contains("MAX_TOKENS"));assertTrue(preview.changes().isEmpty());
        assertTrue(services.products().get(gpu.getId()).isActive());
    }
    @Test void geminiThreeAdminRequestsUseLowThinkingAndEnoughPlanningBudget(){
        var gemini=new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-3.8-flash",true),r->{
            var generation=JsonParser.parseString(requestBody(r)).getAsJsonObject().getAsJsonObject("generationConfig");
            assertEquals("low",generation.getAsJsonObject("thinkingConfig").get("thinkingLevel").getAsString());
            assertEquals(4096,generation.get("maxOutputTokens").getAsInt());
            return call("find_low_stock","{}");
        });
        assertFalse(new AiManagerService(services,gemini).prepare(admin,"Find low stock",false).offline());
    }
    @Test void olderModelsDoNotReceiveUnsupportedThinkingLevels(){
        var gemini=new GeminiAssistant(new GeminiAssistant.Config("test-fixture","gemini-2.5-flash",true),r->{
            var generation=JsonParser.parseString(requestBody(r)).getAsJsonObject().getAsJsonObject("generationConfig");
            assertFalse(generation.has("thinkingConfig"));return call("find_low_stock","{}");
        });
        assertFalse(new AiManagerService(services,gemini).prepare(admin,"Find low stock",false).offline());
    }
    @Test void quotaFailureExplainsTheCooldownAndDoesNotRetryAutomatically(){
        AtomicInteger requests=new AtomicInteger();var manager=new AiManagerService(services,client(r->{requests.incrementAndGet();return new GeminiAssistant.Reply(429,"{}");}));
        var failure=manager.prepare(admin,"Fix missing content",false);assertTrue(failure.aiIssue().contains("HTTP 429"));
        var retry=manager.prepare(admin,"Fix missing content",false);assertTrue(retry.aiIssue().contains("cooling down"));
        assertEquals(1,requests.get());assertTrue(retry.changes().isEmpty());
    }
    @Test void unknownToolsUnsafeArgumentsAndIncompleteResponsesNeverBecomeWrites(){
        assertThrows(ValidationException.class,()->ManagerCommand.parse("execute_sql",new JsonObject()));
        assertThrows(ValidationException.class,()->ManagerCommand.parse("query_products",null));
        assertThrows(ValidationException.class,()->ManagerCommand.parse("query_products",JsonParser.parseString("{\"targetActive\":false}").getAsJsonObject()));
        assertThrows(ValidationException.class,()->ManagerCommand.parse("query_products",JsonParser.parseString("{\"sql\":\"DELETE FROM products\"}").getAsJsonObject()));
        assertThrows(ValidationException.class,()->ManagerCommand.parse("update_status",JsonParser.parseString("{\"targetActive\":\"false\"}").getAsJsonObject()));
        assertThrows(ValidationException.class,()->ManagerCommand.parse("query_products",JsonParser.parseString("{\"stockAtMost\":1.5}").getAsJsonObject()));
        var manager=new AiManagerService(services,client(r->call("drop_database","{}")));
        assertThrows(ValidationException.class,()->manager.prepare(admin,"Delete everything",false));
    }
    @Test void weeklySalesAreCalculatedLocallyWithCalendarBoundariesAndPaidTotals()throws Exception{
        long customer=services.auth().login("customer","customer123").getId();
        try(var c=db.open();var p=c.prepareStatement("INSERT INTO orders(customer_id,created_at,address,payment_method,payment_status,total_cents) VALUES(?,?,?,'Demo',?,?)")){
            for(String[] row:List.of(new String[]{"2026-10-18T15:59:59Z","PAID","100"},new String[]{"2026-10-18T16:00:00Z","PAID","2000"},new String[]{"2026-10-21T06:00:00Z","PENDING","3000"},new String[]{"2026-10-21T16:00:00Z","PAID","9000"})){
                p.setLong(1,customer);p.setString(2,row[0]);p.setString(3,"Private test address");p.setString(4,row[1]);p.setLong(5,Long.parseLong(row[2]));p.executeUpdate();
            }
        }
        var ai=new ManagerAi(client(r->call("sales_summary","{\"period\":\"this_week\",\"includeRestock\":true}")));
        var manager=new AiManagerService(services,ai,Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"),ZoneId.of("Asia/Singapore")));
        var result=manager.prepare(admin,"What did we make this week and what needs restocking?",false);
        assertEquals(2,result.sales().orders());assertEquals(new BigDecimal("50.00"),result.sales().orderValue());assertEquals(new BigDecimal("20.00"),result.sales().paidValue());assertEquals(2,result.products().size());
    }
}
