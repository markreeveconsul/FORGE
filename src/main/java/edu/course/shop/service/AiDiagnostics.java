package edu.course.shop.service;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
/** Logs metadata only: never questions, photos, responses, account identifiers or keys. */
public final class AiDiagnostics {
 public record Event(Instant time,String feature,String model,String outcome,int attempt,int httpStatus,long durationMs,long requestBytes,AiQuota.Info quota){
  public Event(Instant time,String feature,String model,String outcome,int attempt,int httpStatus,long durationMs,long requestBytes){this(time,feature,model,outcome,attempt,httpStatus,durationMs,requestBytes,AiQuota.Info.EMPTY);}
  public String line(){return time+" feature="+safe(feature)+" model="+safe(model)+" outcome="+safe(outcome)+" attempt="+attempt+" http="+httpStatus+" elapsedMs="+durationMs+" bytes="+requestBytes+quotaFields(quota);}
 }
 private static final Deque<Event> recent=new ArrayDeque<>();private static final Path file=Path.of("data","ai-diagnostics.log");
 private AiDiagnostics(){}
 private static String quotaFields(AiQuota.Info quota){
  if(quota.violations().isEmpty()&&quota.retryAfterSeconds()==0)return "";
  StringBuilder text=new StringBuilder(" providerRetryAfterSeconds="+quota.retryAfterSeconds());
  for(var v:quota.violations())text.append(" quotaId=").append(v.id().isBlank()?"unknown":v.id()).append(" quotaMetric=").append(v.metric().isBlank()?"unknown":v.metric()).append(" quotaWindow=").append(v.window()).append(" quotaResource=").append(v.resource()).append(" quotaLimit=").append(v.limit().isBlank()?"unknown":v.limit()).append(" quotaModel=").append(v.model().isBlank()?"unknown":v.model());
  return text.toString();
 }
 static synchronized void record(Event e){
  recent.addLast(e);while(recent.size()>100)recent.removeFirst();
  try{Files.createDirectories(file.getParent());if(Files.exists(file)&&Files.size(file)>512_000)Files.move(file,file.resolveSibling("ai-diagnostics.previous.log"),StandardCopyOption.REPLACE_EXISTING);Files.writeString(file,e.line()+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException|SecurityException ignored){}
 }
 public static synchronized List<Event> recent(){return List.copyOf(recent);}
 private static String safe(String s){return s!=null&&s.matches("[A-Za-z0-9_.-]{1,64}")?s:"unknown";}
}
