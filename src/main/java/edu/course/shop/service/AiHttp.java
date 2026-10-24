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
 private record QuotaReport(Instant at,AiQuota.Info info){}
 private volatile QuotaReport lastQuota;
 AiHttp(GeminiAssistant.Sender sender,String key,String model,Sleeper sleeper,LongSupplier clock,Consumer<AiDiagnostics.Event> events){this.sender=sender;this.key=key;this.model=model;this.sleeper=sleeper;this.clock=clock;this.events=events;}
 GeminiAssistant.Reply send(HttpRequest request,String feature)throws InterruptedException{
  long wait=nextAllowed-clock.getAsLong();
  if(wait>0){
   long seconds=Math.max(1,(wait+999_999_999)/1_000_000_000);AiQuota.Info quota=lastQuota==null?AiQuota.Info.EMPTY:lastQuota.info();
   throw new AiFailure(AiFailure.Kind.COOLDOWN,429,seconds,"FORGE AI is cooling down. The next check is allowed in "+seconds+" seconds; this does not confirm the provider's reset time. "+quota.message(),quota);
  }
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
    failure=error(reply,key);if(reply.status()==429)lastQuota=new QuotaReport(Instant.now(),failure.quota());event(feature,failure.kind().name(),attempt,reply.status(),began,bytes,failure.quota());
    retry=Set.of(408,500,502,503,504).contains(reply.status());
    if(reply.status()==429){
     long seconds=failure.quota().retryAfterSeconds();retry=failure.quota().retryable();delay=seconds*1000;
     if(!retry||attempt==3)nextAllowed=clock.getAsLong()+Duration.ofSeconds(failure.quota().daily()?Math.max(60,seconds):seconds>0?seconds:60).toNanos();
    }
   }catch(InterruptedException e){event(feature,"cancelled",attempt,0,began,bytes);throw e;}
   catch(IOException e){failure=ioFailure(e);event(feature,failure.kind().name(),attempt,0,began,bytes);retry=!(e instanceof UnknownHostException)&&!(e instanceof SSLException);}
   if(!retry||attempt==3)throw failure;
   if(delay==0)delay=(1L<<(attempt-1))*1000+ThreadLocalRandom.current().nextLong(251);
   if(budget-(clock.getAsLong()-start)<=Duration.ofMillis(delay).toNanos())throw failure;
   event(feature,"retrying",attempt,failure.status(),began,bytes,failure.quota());
   try{sleeper.sleep(delay);}catch(InterruptedException e){event(feature,"cancelled",attempt,0,began,bytes);throw e;}
  }
  throw new AssertionError();
 }
 void invalidResponse(String feature){events.accept(new AiDiagnostics.Event(Instant.now(),feature,model,"invalid_response",0,200,0,0));}
 void cacheHit(String feature){events.accept(new AiDiagnostics.Event(Instant.now(),feature,model,"cache_hit",0,0,0,0));}
 private void event(String feature,String outcome,int attempt,int status,long began,long bytes){event(feature,outcome,attempt,status,began,bytes,AiQuota.Info.EMPTY);}
 private void event(String feature,String outcome,int attempt,int status,long began,long bytes,AiQuota.Info quota){events.accept(new AiDiagnostics.Event(Instant.now(),feature,model,outcome,attempt,status,Math.max(0,(clock.getAsLong()-began)/1_000_000),bytes,quota));}
 String limitsSummary(){QuotaReport report=lastQuota;return AiQuota.guide()+"\n\n"+(report==null?"No provider quota details have been reported in this session.":"Last provider limit report: "+report.at()+" (UTC). This is a past report, not live usage; the restriction may have reset.\n"+report.info().message());}
 private static AiFailure timeout(){return new AiFailure(AiFailure.Kind.TIMEOUT,0,0,"FORGE AI timed out waiting for a response. Your internet may still be working. Try a smaller request or retry shortly.");}
 private static AiFailure ioFailure(IOException e){
  if(e instanceof HttpTimeoutException||e instanceof SocketTimeoutException)return timeout();
  String hint=e instanceof UnknownHostException?"The AI server address could not be resolved. Check DNS or network filtering.":e instanceof SSLException?"A secure connection to the AI service could not be established. Check the system clock, proxy or security software.":"The connection to the AI service was interrupted. Your general internet connection may still be working. Try again shortly.";
  return new AiFailure(AiFailure.Kind.CONNECTION,0,0,hint);
 }
 static AiFailure error(GeminiAssistant.Reply reply,String key){
  int status=reply.status();String detail="";AiQuota.Info quota=status==429?AiQuota.read(reply,key):AiQuota.Info.EMPTY;
  try{var error=JsonParser.parseString(reply.body()).getAsJsonObject().getAsJsonObject("error");if(error!=null){if(error.has("message"))detail=error.get("message").getAsString();}}catch(RuntimeException ignored){}

  var kind=switch(status){case 401,403->AiFailure.Kind.ACCESS;case 404->AiFailure.Kind.MODEL;case 429->quota.daily()?AiFailure.Kind.DAILY_QUOTA:AiFailure.Kind.RATE_LIMIT;case 408,504->AiFailure.Kind.TIMEOUT;case 500,502,503->AiFailure.Kind.SERVICE;default->AiFailure.Kind.REQUEST;};
  String message=switch(kind){
   case ACCESS->"AI access was rejected. Check the saved API key, restrictions and project permissions in AI Studio.";
   case MODEL->"The configured AI model is unavailable. Check model access and FORGE_AI_MODEL.";
   case DAILY_QUOTA->quota.message();
   case RATE_LIMIT->quota.message();
   case TIMEOUT->"The AI service timed out. Your internet may still be working. Try a smaller request or retry shortly.";
   case SERVICE->"The AI service is temporarily unavailable. Please try again later.";
   default->status==413?"The AI request is too large. Try a smaller photo or product selection.":"The AI service rejected the request. Check the model and supported settings.";
  };
  if(status==400&&!detail.isBlank()){if(key!=null&&!key.isBlank())detail=detail.replace(key,"[redacted]");detail=detail.replaceAll("AIza[0-9A-Za-z_-]{20,}","[redacted]").replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();message+=" Details: "+detail.substring(0,Math.min(220,detail.length()));}
  return new AiFailure(kind,status,quota.retryAfterSeconds(),"HTTP "+status+": "+message,quota);
 }
}
