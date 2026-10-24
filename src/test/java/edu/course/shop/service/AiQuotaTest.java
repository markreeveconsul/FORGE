package edu.course.shop.service;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AiQuotaTest {
 static GeminiAssistant.Reply reply(String id,String metric,String value,long retry){
  List<Object> details=new ArrayList<>();details.add(Map.of("@type","type.googleapis.com/google.rpc.QuotaFailure","violations",List.of(Map.of("quotaId",id,"quotaMetric",metric,"quotaValue",value,"quotaDimensions",Map.of("model","gemini-test")))));
  if(retry>0)details.add(Map.of("@type","type.googleapis.com/google.rpc.RetryInfo","retryDelay",retry+"s"));
  return new GeminiAssistant.Reply(429,new Gson().toJson(Map.of("error",Map.of("message","Check documentation for daily and minute limits.","details",details))));
 }
 private static final String METRIC="generativelanguage.googleapis.com/generate_content_free_tier_requests";
 @Test void dailyWordsInMessagesAndHelpNeverOverrideMinuteViolation(){
  var r=reply("GenerateRequestsPerMinutePerProjectPerModel-FreeTier",METRIC,"5",30);
  var root=JsonParser.parseString(r.body()).getAsJsonObject();root.getAsJsonObject("error").getAsJsonArray("details").add(new Gson().toJsonTree(Map.of("@type","type.googleapis.com/google.rpc.Help","links",List.of(Map.of("url","https://example.com/requestsPerDay/daily","description","daily quota")))));
  var e=AiHttp.error(new GeminiAssistant.Reply(429,root.toString()),"key");assertEquals(AiFailure.Kind.RATE_LIMIT,e.kind());assertFalse(e.quota().daily());assertTrue(e.getMessage().contains("requests per minute"));assertTrue(e.getMessage().contains("reported limit: 5"));assertTrue(e.getMessage().contains("30 seconds"));
 }
 @Test void structuredInputTokenLimitIsDistinctFromRequests(){
  var e=AiHttp.error(reply("GenerateContentInputTokensPerMinutePerProjectPerModel-FreeTier","generativelanguage.googleapis.com/generate_content_free_tier_input_token_count","250000",15),"key");
  assertEquals(AiQuota.Resource.INPUT_TOKENS,e.quota().violations().getFirst().resource());assertTrue(e.getMessage().contains("input tokens per minute"));assertTrue(e.getMessage().contains("250000"));assertEquals(15,e.retryAfterSeconds());
 }
 @Test void onlyStructuredDayMetadataEstablishesDailyQuota(){
  var e=AiHttp.error(reply("GenerateRequestsPerDayPerProjectPerModel-FreeTier",METRIC,"20",2),"key");assertEquals(AiFailure.Kind.DAILY_QUOTA,e.kind());assertTrue(e.getMessage().contains("requests per day"));assertFalse(e.quota().retryable());
  var unknown=AiHttp.error(new GeminiAssistant.Reply(429,"{\"error\":{\"message\":\"Daily quota exceeded. See requests per day documentation.\"}}"),"key");
  assertEquals(AiFailure.Kind.RATE_LIMIT,unknown.kind());assertTrue(unknown.getMessage().contains("did not identify which limit"));assertTrue(unknown.quota().violations().isEmpty());
 }
 @Test void missingMalformedOrUnrelatedDetailsRemainUnknown(){
  for(String body:List.of("{}","null","[]","not json","{\"error\":{\"details\":{}}}","{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.Help\",\"violations\":[{\"quotaId\":\"RequestsPerDay\"}]}]}}")){
   var e=AiHttp.error(new GeminiAssistant.Reply(429,body),"key");assertEquals(AiFailure.Kind.RATE_LIMIT,e.kind());assertFalse(e.quota().daily());assertFalse(e.quota().retryable());
  }
 }
 @Test void mixedViolationsKeepBothAndNeverRetryDaily(){
  var minute=reply("RequestsPerMinute",METRIC,"5",2);var day=reply("RequestsPerDay",METRIC,"20",2);
  var root=JsonParser.parseString(minute.body()).getAsJsonObject();root.getAsJsonObject("error").getAsJsonArray("details").add(JsonParser.parseString(day.body()).getAsJsonObject().getAsJsonObject("error").getAsJsonArray("details").get(0));
  var info=AiQuota.read(new GeminiAssistant.Reply(429,root.toString()),"key");assertEquals(2,info.violations().size());assertTrue(info.daily());assertFalse(info.retryable());assertTrue(info.message().contains("requests per minute"));assertTrue(info.message().contains("requests per day"));
 }
 @Test void zeroOrInvalidReportedAllowancesNeverInventCounts(){
  var zero=AiQuota.read(reply("RequestsPerMinute",METRIC,"0",2),"key");assertTrue(zero.zeroAllowance());assertFalse(zero.retryable());assertTrue(zero.message().contains("allowance is zero"));
  for(String value:List.of("-1","1.5","1e100000000","9999999999999999999999","private-key")){
   var info=AiQuota.read(reply("RequestsPerMinute",METRIC,value,2),"key");assertEquals("",info.violations().getFirst().limit());assertFalse(info.message().contains("reported limit:"));
  }
 }
 @Test void quotaMetadataIsSafeAndOmitsPrivateDimensions(){
  String key="private-fixture-key";var r=reply("RequestsPerMinute-"+key,METRIC,"5",7);var root=JsonParser.parseString(r.body()).getAsJsonObject();var violation=root.getAsJsonObject("error").getAsJsonArray("details").get(0).getAsJsonObject().getAsJsonArray("violations").get(0).getAsJsonObject();
  violation.addProperty("subject","PRIVATE_ACCOUNT");violation.addProperty("description","PRIVATE_PROMPT daily");violation.getAsJsonObject("quotaDimensions").addProperty("project","PRIVATE_PROJECT");violation.getAsJsonObject("quotaDimensions").addProperty("model","gemini-"+key);
  var info=AiQuota.read(new GeminiAssistant.Reply(429,root.toString()),key);String log=new AiDiagnostics.Event(java.time.Instant.now(),"manager_plan","gemini-test","RATE_LIMIT",1,429,4,100,info).line();
  assertFalse(log.contains(key));assertFalse(log.contains("PRIVATE"));assertFalse(log.contains("private"));assertTrue(log.contains("providerRetryAfterSeconds=7"));assertTrue(log.contains("quotaMetric="+METRIC));assertTrue(log.contains("quotaLimit=5"));
 }
 @Test void retryHintsRequireCorrectTypeAndUseLongestProviderWait(){
  var r=reply("RequestsPerMinute",METRIC,"5",2);var root=JsonParser.parseString(r.body()).getAsJsonObject();root.getAsJsonObject("error").getAsJsonArray("details").add(new Gson().toJsonTree(Map.of("@type","type.googleapis.com/google.rpc.RetryInfo","retryDelay","5.2s")));
  assertEquals(6,AiQuota.read(new GeminiAssistant.Reply(429,root.toString(),Map.of("Retry-After","3")),"key").retryAfterSeconds());
  assertEquals(0,AiQuota.read(new GeminiAssistant.Reply(429,"{\"error\":{\"details\":[{\"@type\":\"google.rpc.Help\",\"retryDelay\":\"10s\"}]}}"),"key").retryAfterSeconds());
 }
 @Test void contradictoryOrCrossFieldFragmentsAreNotDaily(){
  var info=AiQuota.read(reply("RequestsPerMinute","example.googleapis.com/requests_per_day","5",2),"key");assertEquals(AiQuota.Window.UNKNOWN,info.violations().getFirst().window());assertFalse(info.daily());assertFalse(info.retryable());
  assertEquals(AiQuota.Window.UNKNOWN,AiQuota.read(reply("QuotaPer","Day","5",2),"key").violations().getFirst().window());
 }
 @Test void diagnosticsRecordsQuotaIdentifierAndHintWithoutGuessingRemaining(){
  var info=AiQuota.read(reply("RequestsPerMinute",METRIC,"5",20),"key");String line=new AiDiagnostics.Event(java.time.Instant.now(),"chat","gemini-test","RATE_LIMIT",1,429,1,10,info).line();
  assertTrue(line.contains("quotaId=RequestsPerMinute"));assertTrue(line.contains("quotaWindow=MINUTE"));assertTrue(line.contains("quotaResource=REQUESTS"));assertTrue(line.contains("providerRetryAfterSeconds=20"));assertFalse(line.contains("remaining"));
 }
 @Test void cooldownRetainsTheRealQuotaAndHelpIsOnlyAHistoricalReport()throws Exception{
  AtomicLong clock=new AtomicLong();AtomicInteger calls=new AtomicInteger();var events=new ArrayList<AiDiagnostics.Event>();var transport=new AiHttp(r->{calls.incrementAndGet();return reply("RequestsPerMinute",METRIC,"5",30);},"key","gemini-test",ms->{},clock::get,events::add);
  var request=HttpRequest.newBuilder(URI.create("https://example.com")).timeout(Duration.ofSeconds(40)).POST(HttpRequest.BodyPublishers.ofString("test")).build();
  assertThrows(AiFailure.class,()->transport.send(request,"chat"));var failure=assertThrows(AiFailure.class,()->transport.send(request,"chat"));
  assertEquals(AiFailure.Kind.COOLDOWN,failure.kind());assertEquals("5",failure.quota().violations().getFirst().limit());assertTrue(failure.getMessage().contains("requests per minute"));assertEquals(1,calls.get());
  assertTrue(transport.limitsSummary().contains("past report, not live usage"));assertTrue(transport.limitsSummary().contains("cannot reliably show how many requests remain"));
 }
 @Test void unknownAndZeroQuotasAreNotAutomaticallyRetriedWithShortHints()throws Exception{
  for(var reply:List.of(new GeminiAssistant.Reply(429,"{}",Map.of("Retry-After","2")),reply("RequestsPerMinute",METRIC,"0",2))){
   AtomicInteger calls=new AtomicInteger();var transport=new AiHttp(r->{calls.incrementAndGet();return reply;},"key","gemini-test",ms->{throw new AssertionError("No automatic retry");},System::nanoTime,e->{});
   assertThrows(AiFailure.class,()->transport.send(HttpRequest.newBuilder(URI.create("https://example.com")).POST(HttpRequest.BodyPublishers.ofString("test")).build(),"chat"));assertEquals(1,calls.get());
  }
 }
 @Test void nonQuotaStatusesIgnoreQuotaDetails(){
  var quota=reply("RequestsPerDay",METRIC,"20",0);var e=AiHttp.error(new GeminiAssistant.Reply(503,quota.body()),"key");assertEquals(AiFailure.Kind.SERVICE,e.kind());assertTrue(e.quota().violations().isEmpty());
 }
}
