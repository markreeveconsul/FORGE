package edu.course.shop.service;

import com.google.gson.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Only structured quota violations establish which allowance was exceeded. */
public final class AiQuota {
    public enum Window { MINUTE, DAY, UNKNOWN }
    public enum Resource { REQUESTS, INPUT_TOKENS, OUTPUT_TOKENS, TOKENS, SEARCHES, IMAGES, UNKNOWN }
    public record Violation(String metric,String id,String limit,String model,Window window,Resource resource) {
        public Violation {
            metric=identifier(metric);id=identifier(id);model=model!=null&&model.matches("gemini-[A-Za-z0-9.-]{1,80}")?model:"";
            limit=amount(limit);window=window==null?Window.UNKNOWN:window;resource=resource==null?Resource.UNKNOWN:resource;
        }
        public String label(){
            String item=switch(resource){case REQUESTS->"requests";case INPUT_TOKENS->"input tokens";case OUTPUT_TOKENS->"output tokens";case TOKENS->"tokens";case SEARCHES->"search requests";case IMAGES->"images";default->"usage";};
            String period=switch(window){case MINUTE->" per minute";case DAY->" per day";default->" (time window not supplied)";};
            return item+period+(limit.isBlank()?"":" — reported limit: "+limit);
        }
    }
    public record Info(List<Violation> violations,long retryAfterSeconds) {
        public static final Info EMPTY=new Info(List.of(),0);
        public Info {violations=List.copyOf(violations);retryAfterSeconds=Math.max(0,Math.min(86400,retryAfterSeconds));}
        public boolean daily(){return violations.stream().anyMatch(v->v.window()==Window.DAY);}
        public boolean zeroAllowance(){return violations.stream().anyMatch(v->v.limit().equals("0"));}
        public boolean retryable(){return !violations.isEmpty()&&!zeroAllowance()&&violations.stream().allMatch(v->v.window()==Window.MINUTE)&&retryAfterSeconds>0&&retryAfterSeconds<=10;}
        public String message(){
            String text;
            if(violations.isEmpty())text="The AI provider rejected this request with a usage-limit error, but did not identify which limit was exceeded. This does not confirm that the daily quota is exhausted.";
            else text="FORGE AI reached the provider limit for "+violations.stream().map(Violation::label).collect(java.util.stream.Collectors.joining("; "))+".";
            if(zeroAllowance())text+=" The reported allowance is zero. Ask the store administrator to check AI access and project limits.";
            else if(daily())text+=" Please try again after the daily allowance resets. A short retry hint does not confirm the daily reset time.";
            else if(retryAfterSeconds>0)text+=" The provider suggests waiting "+retryAfterSeconds+" seconds before retrying; this is not a guarantee that the allowance will be available then.";
            else text+=" No retry time was supplied. Please try again later.";
            return text+" Browsing, your cart and local store information remain available.";
        }
    }
    private AiQuota(){}
    public static String guide(){
        return "Online AI features share the store's provider allowance. Separate limits can apply to requests per minute, input tokens per minute and requests per day. Being below a daily limit does not rule out a minute limit.\n\n"
            +"One command may make more than one AI request. Retries can also use requests. Browsing, cart actions and local store information remain available when online AI is limited.\n\n"
            +"FORGE displays limits reported by the provider, not a live usage counter. It cannot reliably show how many requests remain. Limits vary by model and project; the store administrator can check the active limits in AI Studio.";
    }
    static Info read(GeminiAssistant.Reply reply,String key){
        List<Violation> found=new ArrayList<>();
        JsonArray details=details(reply.body());
        if(details!=null)for(JsonElement element:details){
            if(!element.isJsonObject())continue;JsonObject detail=element.getAsJsonObject();
            if(!type(detail,"google.rpc.QuotaFailure")||!detail.has("violations")||!detail.get("violations").isJsonArray())continue;
            for(JsonElement entry:detail.getAsJsonArray("violations")){
                if(found.size()>=8)break;if(!entry.isJsonObject())continue;JsonObject v=entry.getAsJsonObject();
                String metric=safeField(v,"quotaMetric",key),id=safeField(v,"quotaId",key),model="";
                if(v.has("quotaDimensions")&&v.get("quotaDimensions").isJsonObject())model=safeField(v.getAsJsonObject("quotaDimensions"),"model",key);
                String limit=v.has("quotaValue")&&v.get("quotaValue").isJsonPrimitive()?amount(v.get("quotaValue").getAsString()):"";
                // Descriptions, subjects, documentation links and error.message are never classifiers or log fields.
                String identity=(id+" "+metric).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                Window idWindow=window(id),metricWindow=window(metric);Window window=idWindow!=Window.UNKNOWN&&metricWindow!=Window.UNKNOWN&&idWindow!=metricWindow?Window.UNKNOWN:idWindow!=Window.UNKNOWN?idWindow:metricWindow;
                Resource resource=identity.contains("inputtoken")?Resource.INPUT_TOKENS:identity.contains("outputtoken")?Resource.OUTPUT_TOKENS:identity.contains("token")?Resource.TOKENS:identity.contains("grounding")||identity.contains("search")?Resource.SEARCHES:identity.contains("image")?Resource.IMAGES:identity.contains("request")?Resource.REQUESTS:Resource.UNKNOWN;
                found.add(new Violation(metric,id,limit,model,window,resource));
            }
        }
        return new Info(found,retryDelay(reply,details));
    }
    private static JsonArray details(String body){try{JsonObject error=JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");return error!=null&&error.has("details")&&error.get("details").isJsonArray()?error.getAsJsonArray("details"):null;}catch(RuntimeException ignored){return null;}}
    private static boolean type(JsonObject object,String expected){String type=text(object,"@type");return type.equals(expected)||type.equals("type.googleapis.com/"+expected);}
    private static String text(JsonObject object,String field){return object.has(field)&&object.get(field).isJsonPrimitive()&&object.getAsJsonPrimitive(field).isString()?object.get(field).getAsString():"";}
    private static String safeField(JsonObject object,String field,String key){String value=text(object,field);if(key!=null&&!key.isBlank()&&value.contains(key))return "";return identifier(value);}
    private static String identifier(String value){return value!=null&&value.matches("[A-Za-z][A-Za-z0-9_./:-]{0,180}")&&!value.matches(".*AIza[0-9A-Za-z_-]{20,}.*")?value:"";}
    private static String amount(String value){if(value==null||!value.matches("[0-9]{1,19}"))return "";try{var number=new java.math.BigInteger(value);return number.bitLength()<64?number.toString():"";}catch(RuntimeException ignored){return "";}}
    private static Window window(String identity){String text=identity.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");return text.contains("perday")||text.contains("daily")?Window.DAY:text.contains("perminute")?Window.MINUTE:Window.UNKNOWN;}
    private static long retryDelay(GeminiAssistant.Reply reply,JsonArray details){
        long seconds=0;String header=reply.headers().getOrDefault("retry-after","");
        if(!header.isBlank())try{seconds=bounded(Long.parseLong(header.trim()));}catch(NumberFormatException ignored){try{seconds=bounded(Duration.between(Instant.now(),ZonedDateTime.parse(header,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds());}catch(RuntimeException invalid){}}
        if(details!=null)for(JsonElement element:details)try{
            if(!element.isJsonObject()||!type(element.getAsJsonObject(),"google.rpc.RetryInfo"))continue;
            String delay=text(element.getAsJsonObject(),"retryDelay");if(!delay.matches("[0-9]+(?:\\.[0-9]{1,9})?s"))continue;
            double reported=Double.parseDouble(delay.substring(0,delay.length()-1));if(Double.isFinite(reported))seconds=Math.max(seconds,bounded((long)Math.ceil(reported)));
        }catch(RuntimeException ignored){}
        return seconds;
    }
    private static long bounded(long value){return Math.max(0,Math.min(86400,value));}
}
