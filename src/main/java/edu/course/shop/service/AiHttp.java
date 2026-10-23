package edu.course.shop.service;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.*;
import javax.net.ssl.SSLException;

/** Bounded retries for generation only; never executes database writes. */
final class AiHttp {
 @FunctionalInterface interface Sleeper {void sleep(long millis)throws InterruptedException;}
 private final GeminiAssistant.Sender sender;private final String key,model;private final Sleeper sleeper;
 private final LongSupplier clock;private final Consumer<AiDiagnostics.Event> events;private volatile long nextAllowed;
 AiHttp(GeminiAssistant.Sender sender,String key,String model,Sleeper sleeper,LongSupplier clock,Consumer<AiDiagnostics.Event> events){this.sender=sender;this.key=key;this.model=model;this.sleeper=sleeper;this.clock=clock;this.events=events;}
 GeminiAssistant.Reply send(HttpRequest request,String feature)throws InterruptedException{
  long wait=nextAllowed-clock.getAsLong();
  if(wait>0)throw new AiFailure(AiFailure.Kind.COOLDOWN,429,Math.max(1,(wait+999_999_999)/1_000_000_000),"FORGE AI is cooling down. Wait "+Math.max(1,(wait+999_999_999)/1_000_000_000)+" seconds before retrying. Other AI features share this limit.");
  long start=clock.getAsLong(),budget=Duration.ofSeconds(90).toNanos();
  long bytes=request.bodyPublisher().map(HttpRequest.BodyPublisher::contentLength).orElse(0L);
  for(int attempt=1;attempt<=3;attempt++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   long remaining=budget-(clock.getAsLong()-start);if(remaining<=0)throw timeout();
   HttpRequest current=HttpRequest.newBuilder(request,(n,v)->true).timeout(Duration.ofNanos(Math.min(remaining,request.timeout().orElse(Duration.ofSeconds(60)).toNanos()))).build();
   long began=clock.getAsLong(),delay=0;AiFailure failure;boolean retry;
   try{
    var reply=sender.send(current);
    if(reply.status()==200){event(feature,"success",attempt,200,began,bytes);return reply;}
    failure=error(reply,key);event(feature,failure.kind().name(),attempt,reply.status(),began,bytes);
    retry=Set.of(408,500,502,503,504).contains(reply.status());
    if(reply.status()==429){
     long seconds=retryDelay(reply);retry=failure.kind()!=AiFailure.Kind.DAILY_QUOTA&&seconds>0&&seconds<=10;delay=seconds*1000;
     if(!retry||attempt==3)nextAllowed=clock.getAsLong()+Duration.ofSeconds(seconds>0?seconds:60).toNanos();
    }
   }catch(InterruptedException e){event(feature,"cancelled",attempt,0,began,bytes);throw e;}
   catch(IOException e){failure=ioFailure(e);event(feature,failure.kind().name(),attempt,0,began,bytes);retry=!(e instanceof UnknownHostException)&&!(e instanceof SSLException);}
   if(!retry||attempt==3)throw failure;
   if(delay==0)delay=(1L<<(attempt-1))*1000+ThreadLocalRandom.current().nextLong(251);
   if(budget-(clock.getAsLong()-start)<=Duration.ofMillis(delay).toNanos())throw failure;
   event(feature,"retrying",attempt,failure.status(),began,bytes);
   try{sleeper.sleep(delay);}catch(InterruptedException e){event(feature,"cancelled",attempt,0,began,bytes);throw e;}
  }
  throw new AssertionError();
 }
 void invalidResponse(String feature){events.accept(new AiDiagnostics.Event(Instant.now(),feature,model,"invalid_response",0,200,0,0));}
 void cacheHit(String feature){events.accept(new AiDiagnostics.Event(Instant.now(),feature,model,"cache_hit",0,0,0,0));}
 private void event(String feature,String outcome,int attempt,int status,long began,long bytes){events.accept(new AiDiagnostics.Event(Instant.now(),feature,model,outcome,attempt,status,Math.max(0,(clock.getAsLong()-began)/1_000_000),bytes));}
 private static AiFailure timeout(){return new AiFailure(AiFailure.Kind.TIMEOUT,0,0,"FORGE AI timed out waiting for a response. Your internet may still be working. Try a smaller request or retry shortly.");}
 private static AiFailure ioFailure(IOException e){
  if(e instanceof HttpTimeoutException||e instanceof SocketTimeoutException)return timeout();
  String hint=e instanceof UnknownHostException?"The AI server address could not be resolved. Check DNS or network filtering.":e instanceof SSLException?"A secure connection to the AI service could not be established. Check the system clock, proxy or security software.":"The connection to the AI service was interrupted. Your general internet connection may still be working. Try again shortly.";
  return new AiFailure(AiFailure.Kind.CONNECTION,0,0,hint);
 }
 static AiFailure error(GeminiAssistant.Reply reply,String key){
  int status=reply.status();String detail="",data="";
  try{var error=JsonParser.parseString(reply.body()).getAsJsonObject().getAsJsonObject("error");if(error!=null){data=error.toString().toLowerCase(Locale.ROOT);if(error.has("message"))detail=error.get("message").getAsString();}}catch(RuntimeException ignored){}
  boolean daily=data.matches("(?s).*(per.?day|daily|requestsperday|tokensperday|rpd|tpd).*");
  var kind=switch(status){case 401,403->AiFailure.Kind.ACCESS;case 404->AiFailure.Kind.MODEL;case 429->daily?AiFailure.Kind.DAILY_QUOTA:AiFailure.Kind.RATE_LIMIT;case 408,504->AiFailure.Kind.TIMEOUT;case 500,502,503->AiFailure.Kind.SERVICE;default->AiFailure.Kind.REQUEST;};
  String message=switch(kind){
   case ACCESS->"AI access was rejected. Check the saved API key, restrictions and project permissions in AI Studio.";
   case MODEL->"The configured AI model is unavailable. Check model access and FORGE_AI_MODEL.";
   case DAILY_QUOTA->"The daily AI quota was reached. Wait for the quota reset shown in AI Studio; repeated retries will not fix it.";
   case RATE_LIMIT->"The AI quota or rate limit was reached. Wait before retrying and check your active limits in AI Studio.";
   case TIMEOUT->"The AI service timed out. Your internet may still be working. Try a smaller request or retry shortly.";
   case SERVICE->"The AI service is temporarily unavailable. Please try again later.";
   default->status==413?"The AI request is too large. Try a smaller photo or product selection.":"The AI service rejected the request. Check the model and supported settings.";
  };
  if(status==400&&!detail.isBlank()){if(key!=null&&!key.isBlank())detail=detail.replace(key,"[redacted]");detail=detail.replaceAll("AIza[0-9A-Za-z_-]{20,}","[redacted]").replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();message+=" Details: "+detail.substring(0,Math.min(220,detail.length()));}
  return new AiFailure(kind,status,retryDelay(reply),"HTTP "+status+": "+message);
 }
 private static long retryDelay(GeminiAssistant.Reply reply){
  String header=reply.headers().getOrDefault("retry-after","");
  if(!header.isBlank())try{return Math.max(1,Math.min(86400,Long.parseLong(header.trim())));}catch(NumberFormatException ignored){try{return Math.max(1,Math.min(86400,Duration.between(Instant.now(),ZonedDateTime.parse(header,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()));}catch(RuntimeException invalid){}}
  try{var details=JsonParser.parseString(reply.body()).getAsJsonObject().getAsJsonObject("error").getAsJsonArray("details");if(details!=null)for(var e:details){var info=e.getAsJsonObject();if(info.has("retryDelay")){double seconds=Double.parseDouble(info.get("retryDelay").getAsString().replace("s",""));if(Double.isFinite(seconds)&&seconds>0)return (long)Math.ceil(Math.min(seconds,86400));}}}catch(RuntimeException ignored){}
  return 0;
 }
}
