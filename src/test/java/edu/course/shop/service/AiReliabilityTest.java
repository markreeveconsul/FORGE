package edu.course.shop.service;
import com.google.gson.*;
import edu.course.shop.model.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class AiReliabilityTest {
 private static final String GOOD="{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Recorded facts only.\"}]}}]}";
 private final AtomicLong time=new AtomicLong();
 private final List<Long> waits=new ArrayList<>();
 private final List<AiDiagnostics.Event> events=new ArrayList<>();
 private GeminiAssistant client(GeminiAssistant.Sender sender){return new GeminiAssistant(new GeminiAssistant.Config("private-fixture-key","gemini-3.8-flash",true),sender,ms->{waits.add(ms);time.addAndGet(ms*1_000_000);},time::get,events::add);}
 private HttpRequest request(){return HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/test")).timeout(Duration.ofSeconds(40)).header("x-goog-api-key","private-fixture-key").POST(HttpRequest.BodyPublishers.ofString("PRIVATE QUESTION")).build();}
 private AiHttp http(GeminiAssistant.Sender sender){return new AiHttp(sender,"private-fixture-key","gemini-test",ms->{waits.add(ms);time.addAndGet(ms*1_000_000);},time::get,events::add);}
 @Test void transientServiceFailureRecoversWithBoundedBackoff()throws Exception{
  AtomicInteger count=new AtomicInteger();var transport=http(r->new GeminiAssistant.Reply(count.incrementAndGet()<3?503:200,GOOD));
  assertEquals(200,transport.send(request(),"chat").status());assertEquals(3,count.get());assertEquals(2,waits.size());assertTrue(waits.get(0)>=1000&&waits.get(1)>=2000);
 }
 @Test void repeatedOutageStopsAfterThreeAttempts()throws Exception{
  AtomicInteger count=new AtomicInteger();var failure=assertThrows(AiFailure.class,()->http(r->{count.incrementAndGet();return new GeminiAssistant.Reply(503,"{}");}).send(request(),"manager"));
  assertEquals(AiFailure.Kind.SERVICE,failure.kind());assertEquals(3,count.get());assertEquals(2,waits.size());
 }
 @Test void dailyQuotaNeverRetriesEvenWithShortRetryHint()throws Exception{
  AtomicInteger count=new AtomicInteger();var transport=http(r->{count.incrementAndGet();return new GeminiAssistant.Reply(429,"{\"error\":{\"message\":\"Daily free request quota exceeded\"}}",Map.of("Retry-After","2"));});
  assertEquals(AiFailure.Kind.DAILY_QUOTA,assertThrows(AiFailure.class,()->transport.send(request(),"chat")).kind());
  assertEquals(AiFailure.Kind.COOLDOWN,assertThrows(AiFailure.class,()->transport.send(request(),"chat")).kind());assertEquals(1,count.get());assertTrue(waits.isEmpty());
 }
 @Test void unknownQuotaDoesNotSpendMoreRequests()throws Exception{
  AtomicInteger count=new AtomicInteger();var transport=http(r->{count.incrementAndGet();return new GeminiAssistant.Reply(429,"{}");});
  assertEquals(AiFailure.Kind.RATE_LIMIT,assertThrows(AiFailure.class,()->transport.send(request(),"chat")).kind());assertEquals(1,count.get());assertTrue(waits.isEmpty());
 }
 @Test void explicitShortRateLimitHonorsProviderDelay()throws Exception{
  AtomicInteger count=new AtomicInteger();var transport=http(r->count.incrementAndGet()==1?new GeminiAssistant.Reply(429,"{}",Map.of("Retry-After","2")):new GeminiAssistant.Reply(200,GOOD));
  assertEquals(200,transport.send(request(),"chat").status());assertEquals(List.of(2000L),waits);assertEquals(2,count.get());
 }
 @Test void accessModelAndInvalidRequestsNeverRetry()throws Exception{
  for(int status:new int[]{400,401,403,404,413}){AtomicInteger count=new AtomicInteger();var transport=http(r->{count.incrementAndGet();return new GeminiAssistant.Reply(status,"{\"error\":{\"message\":\"invalid setting private-fixture-key\"}}");});
   var e=assertThrows(AiFailure.class,()->transport.send(request(),"manager"));assertEquals(1,count.get());assertFalse(e.getMessage().contains("private-fixture-key"));}assertTrue(waits.isEmpty());
 }
 @Test void timeoutsRemainTimeoutsAndRespectTotalDeadline()throws Exception{
  AtomicInteger count=new AtomicInteger();List<Duration> limits=new ArrayList<>();var transport=http(r->{limits.add(r.timeout().orElseThrow());count.incrementAndGet();time.addAndGet(Duration.ofSeconds(40).toNanos());throw new HttpTimeoutException("private provider detail");});
  var e=assertThrows(AiFailure.class,()->transport.send(request(),"chat"));assertEquals(AiFailure.Kind.TIMEOUT,e.kind());assertTrue(count.get()<=3);assertTrue(limits.getLast().compareTo(Duration.ofSeconds(10))<0);assertFalse(e.getMessage().contains("private provider detail"));
 }
 @Test void dnsAndTlsAreDistinctFromQuotaAndNotRetried()throws Exception{
  for(IOException cause:List.of(new UnknownHostException("private host"),new javax.net.ssl.SSLException("private certificate"))){AtomicInteger count=new AtomicInteger();var transport=http(r->{count.incrementAndGet();throw cause;});
   var e=assertThrows(AiFailure.class,()->transport.send(request(),"chat"));assertEquals(AiFailure.Kind.CONNECTION,e.kind());assertEquals(1,count.get());assertFalse(e.getMessage().contains("private "));}
 }
 @Test void cancellationDuringBackoffStopsImmediately()throws Exception{
  AtomicInteger count=new AtomicInteger();var transport=new AiHttp(r->{count.incrementAndGet();return new GeminiAssistant.Reply(503,"{}");},"private-fixture-key","gemini-test",ms->{throw new InterruptedException();},time::get,events::add);
  assertThrows(InterruptedException.class,()->transport.send(request(),"chat"));assertEquals(1,count.get());assertEquals("cancelled",events.getLast().outcome());
 }
 @Test void diagnosticsContainOnlySafeMetadata()throws Exception{
  var transport=http(r->{throw new IOException("private-fixture-key PRIVATE QUESTION PRIVATE ADDRESS");});
  assertThrows(AiFailure.class,()->transport.send(request(),"image_search"));String log=events.stream().map(AiDiagnostics.Event::line).reduce("",(a,b)->a+b);
  assertFalse(log.contains("private-fixture-key"));assertFalse(log.contains("PRIVATE"));assertTrue(log.contains("bytes=16"));assertTrue(log.contains("CONNECTION"));
 }
 @Test void cacheExpiresEvictsAndHonorsByteLimit(){
  var cache=new AiCache<String>(2,8,10,time::get);cache.put("a","A",3);cache.put("b","B",3);assertEquals("A",cache.get("a"));cache.put("c","C",3);assertNull(cache.get("b"));cache.put("huge","X",9);assertNull(cache.get("huge"));time.set(10);assertNull(cache.get("a"));cache.clear();assertNull(cache.get("c"));
 }
 @Test void repeatedPublicAnswerUsesCacheButChangedFactsDoNot(){
  AtomicInteger count=new AtomicInteger();var gemini=client(r->{count.incrementAndGet();return new GeminiAssistant.Reply(200,GOOD);});
  JsonObject context=new JsonObject();context.addProperty("stock",4);context.addProperty("productId",1);
  gemini.answer("What is listed?",context);gemini.answer("What is listed?",context);assertEquals(1,count.get());assertEquals("cache_hit",events.getLast().outcome());
  time.addAndGet(Duration.ofSeconds(4).toNanos());context.addProperty("stock",3);gemini.answer("What is listed?",context);assertEquals(2,count.get());
  gemini.clearCaches();time.addAndGet(Duration.ofSeconds(4).toNanos());gemini.answer("What is listed?",context);assertEquals(3,count.get());
 }
 @Test void privateOrderAndCartAnswersAreNeverCached(){
  AtomicInteger count=new AtomicInteger();var gemini=client(r->{count.incrementAndGet();return new GeminiAssistant.Reply(200,GOOD);});JsonObject context=new JsonObject();context.addProperty("orderId",123);
  gemini.answer("Explain",context);time.addAndGet(Duration.ofSeconds(4).toNanos());gemini.answer("Explain",context);assertEquals(2,count.get());
  context=new JsonObject();context.addProperty("cartSnapshotTotalUSD",10);time.addAndGet(Duration.ofSeconds(4).toNanos());gemini.answer("Explain",context);time.addAndGet(Duration.ofSeconds(4).toNanos());gemini.answer("Explain",context);assertEquals(4,count.get());
 }
 @Test void cacheLifetimeEndsAfterTwoMinutes(){
  AtomicInteger count=new AtomicInteger();var gemini=client(r->{count.incrementAndGet();return new GeminiAssistant.Reply(200,GOOD);});JsonObject context=new JsonObject();context.addProperty("productId",1);gemini.answer("Help",context);time.addAndGet(Duration.ofMinutes(2).toNanos());gemini.answer("Help",context);assertEquals(2,count.get());
 }
 private static byte[] photo()throws Exception{
  var image=new BufferedImage(1024,800,BufferedImage.TYPE_INT_RGB);Random random=new Random(1);for(int y=0;y<800;y++)for(int x=0;x<1024;x++)image.setRGB(x,y,random.nextInt());var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();
 }
 private static String visualReply(){String result="{\"identifiedItem\":\"graphics card\",\"matches\":[{\"productId\":41,\"confidence\":95,\"reason\":\"Same form\"}]}";return new Gson().toJson(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text",result)))))));}
 @Test void visualSearchShrinksPayloadCachesAndInvalidatesWithCatalog()throws Exception{
  byte[] photo=photo();Product gpu=new Product(41,"GPU",Category.PC_PARTS,java.math.BigDecimal.TEN,4,true,"",photo);AtomicInteger count=new AtomicInteger();
  var gemini=client(r->{count.incrementAndGet();JsonObject body=JsonParser.parseString(AiManagerServiceTest.requestBody(r)).getAsJsonObject();var parts=body.getAsJsonArray("contents").get(0).getAsJsonObject().getAsJsonArray("parts");
   List<Integer> sizes=new ArrayList<>();for(var p:parts)if(p.getAsJsonObject().has("inline_data")){try{byte[] b=Base64.getDecoder().decode(p.getAsJsonObject().getAsJsonObject("inline_data").get("data").getAsString());var decoded=ImageIO.read(new ByteArrayInputStream(b));sizes.add(Math.max(decoded.getWidth(),decoded.getHeight()));}catch(IOException e){throw new AssertionError(e);}}
   assertEquals(List.of(512,256),sizes);assertTrue(r.bodyPublisher().orElseThrow().contentLength()<150_000);var gen=body.getAsJsonObject("generationConfig");assertFalse(gen.has("temperature"));assertEquals("low",gen.getAsJsonObject("thinkingConfig").get("thinkingLevel").getAsString());return new GeminiAssistant.Reply(200,visualReply());});
  assertEquals(41,gemini.searchByImage(photo,List.of(gpu)).matches().getFirst().productId());gemini.searchByImage(photo,List.of(gpu));assertEquals(1,count.get());
  time.addAndGet(Duration.ofSeconds(4).toNanos());Product changed=new Product(41,"GPU",Category.PC_PARTS,java.math.BigDecimal.TEN,3,true,"",photo);gemini.searchByImage(photo,List.of(changed));assertEquals(2,count.get());
 }
 @Test void failedOrMalformedRepliesAreNeverCached(){
  AtomicInteger count=new AtomicInteger();var gemini=client(r->new GeminiAssistant.Reply(200,count.incrementAndGet()==1?"{}":GOOD));assertThrows(IllegalStateException.class,()->gemini.answer("Help",new JsonObject()));time.addAndGet(Duration.ofSeconds(4).toNanos());assertEquals("Recorded facts only.",gemini.answer("Help",new JsonObject()));assertEquals(2,count.get());
 }

 @Test void visualResponseIgnoresThinkingAndRejectsTruncation()throws Exception{
  byte[] photo=photo();Product gpu=new Product(41,"GPU",Category.PC_PARTS,java.math.BigDecimal.TEN,4,true,"",photo);
  JsonObject response=JsonParser.parseString(visualReply()).getAsJsonObject();var parts=response.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content").getAsJsonArray("parts");
  parts.add(new Gson().toJsonTree(Map.of("thought",true,"text","Internal notes that are not JSON")));
  var gemini=client(r->new GeminiAssistant.Reply(200,response.toString()));assertEquals(1,gemini.searchByImage(photo,List.of(gpu)).matches().size());
  var truncated=client(r->new GeminiAssistant.Reply(200,visualReply().replace("STOP","MAX_TOKENS")));
  assertEquals(AiFailure.Kind.RESPONSE,assertThrows(AiFailure.class,()->truncated.searchByImage(photo,List.of(gpu))).kind());
  assertEquals("invalid_response",events.getLast().outcome());
 }
 @Test void changingPhotoOrExpiringResultMakesANewVisualRequest()throws Exception{
  byte[] photo=photo();Product gpu=new Product(41,"GPU",Category.PC_PARTS,java.math.BigDecimal.TEN,4,true,"",photo);AtomicInteger count=new AtomicInteger();
  var gemini=client(r->{count.incrementAndGet();return new GeminiAssistant.Reply(200,visualReply());});gemini.searchByImage(photo,List.of(gpu));
  time.addAndGet(Duration.ofMinutes(2).toNanos());gemini.searchByImage(photo,List.of(gpu));assertEquals(2,count.get());
  time.addAndGet(Duration.ofSeconds(4).toNanos());var different=new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();ImageIO.write(different,"png",bytes);
  Product changed=new Product(41,"GPU",Category.PC_PARTS,java.math.BigDecimal.TEN,4,true,"",bytes.toByteArray());gemini.searchByImage(photo,List.of(changed));assertEquals(3,count.get());
 }
 @Test void sharedSpacingKeepsItsCauseAndMalformedShapesAreReported(){
  var gemini=client(r->new GeminiAssistant.Reply(200,GOOD));gemini.answer("Help",new JsonObject());
  assertEquals(AiFailure.Kind.COOLDOWN,assertThrows(AiFailure.class,()->gemini.managerRequest(new JsonObject())).kind());
  for(String body:List.of("[]","null","{\"candidates\":{}}"))assertEquals(AiFailure.Kind.RESPONSE,assertThrows(AiFailure.class,()->GeminiAssistant.parse(new GeminiAssistant.Reply(200,body))).kind());
 }}
