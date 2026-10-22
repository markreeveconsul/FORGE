package edu.course.shop.model;

import edu.course.shop.exception.ValidationException;
import java.math.BigDecimal;
import java.util.*;

/** Named, category-aware fields serialized to the existing key:value format without losing custom facts. */
public final class ProductSpecifications {
    private ProductSpecifications(){}
    private static final Map<String,String> ALIASES=Map.ofEntries(
        Map.entry("manufacturer","Brand"),Map.entry("model number","Model"),Map.entry("ram","RAM"),
        Map.entry("ram capacity","RAM"),Map.entry("memory capacity","RAM"),Map.entry("vram","VRAM"),
        Map.entry("video memory","VRAM"),Map.entry("storage capacity","Storage"),Map.entry("socket type","Socket"),
        Map.entry("cpu socket","Socket"),Map.entry("ram type","Memory type"),Map.entry("gpu length","Length"),
        Map.entry("max gpu length","Maximum GPU length"),Map.entry("recommended power supply","Recommended PSU"));
    public static List<String> fields(Category category){
        List<String> keys=new ArrayList<>(List.of("Brand","Model","SKU","Variant","Warranty"));
        keys.addAll(switch(category==null?Category.ELECTRONICS:category){
            case PC_SETS->List.of("CPU","GPU","RAM","Memory type","Storage","Operating system","Wattage");
            case LAPTOPS->List.of("CPU","GPU","RAM","Memory type","Storage","Screen size","Refresh rate","Operating system");
            case PC_PARTS->List.of("Component type","Socket","Memory type","RAM","VRAM","Storage","Length","Maximum GPU length","Recommended PSU","Wattage");
            case PHONES_TABLETS->List.of("RAM","Storage","Screen size","Operating system","Color");
            case PERIPHERALS->List.of("Connection","Interface","Layout","Color");
            default->List.of("Connection","Color");
        });return List.copyOf(keys);
    }
    public static String canonical(String key){
        String k=key.trim().replaceAll("\\s+"," ");String lower=k.toLowerCase(Locale.ROOT);
        if(ALIASES.containsKey(lower))return ALIASES.get(lower);
        for(Category category:Category.shopping())for(String name:fields(category))if(name.equalsIgnoreCase(k))return name;
        return k;
    }
    public static Map<String,String> values(String text){
        Map<String,String> result=new LinkedHashMap<>();
        if(text!=null)for(String line:text.lines().toList()){int split=line.indexOf(':');if(split>0){String key=canonical(line.substring(0,split)),value=line.substring(split+1).trim();if(!value.isEmpty())result.put(key,value);}}
        return Collections.unmodifiableMap(result);
    }
    public static String value(Product p,String key){return values(p.getSpecifications()).getOrDefault(canonical(key),"");}
    public static boolean matches(Product p,Map<String,String> filters){return filters.entrySet().stream().allMatch(e->value(p,e.getKey()).equalsIgnoreCase(e.getValue()));}
    /** Strict parsing remains available when a calculation requires a known numeric format. */
    public static String validated(String text){return normalize(text,true);}
    /** Product listings accept free-form facts; known numeric formats are normalized when possible. */
    public static String listingText(String text){return normalize(text,false);}
    public record FormatWarning(String key,String message) { }
    public static List<FormatWarning> formatWarnings(String text){
        List<FormatWarning> warnings=new ArrayList<>();
        if(text!=null)for(String line:text.lines().filter(s->!s.isBlank()).toList()){
            int split=line.indexOf(':');if(split<1)continue;String key=canonical(line.substring(0,split));
            try{validated(line);}catch(ValidationException error){String message=error.getMessage();
                if(message.startsWith(key+":"))warnings.add(new FormatWarning(key,"For consistent filters, "+message.substring(key.length()+1).trim().replaceFirst("^Use","use")+" You can still save this value as entered."));
            }
        }
        return List.copyOf(warnings);
    }
    private static String normalize(String text,boolean strict){
        if(text==null||text.isBlank())return "";
        if(text.length()>1000)throw new ValidationException("Specifications must be at most 1,000 characters.");
        StringJoiner output=new StringJoiner("\n");Set<String> seen=new HashSet<>();
        for(String line:text.lines().filter(s->!s.isBlank()).toList()){
            int split=line.indexOf(':');if(split<1){output.add(line.trim());continue;}
            String key=canonical(line.substring(0,split)),value=line.substring(split+1).trim();
            if(key.length()>60||value.length()>200)throw new ValidationException("Keep each specification name within 60 characters and value within 200.");
            if(value.isEmpty())continue;
            if(!seen.add(key.toLowerCase(Locale.ROOT)))throw new ValidationException("Enter "+key+" only once.");
            try{
            if(Set.of("RAM","VRAM","Storage").contains(key))value=quantity(key,value,"(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(GB|TB|MB)","Use a positive capacity with a unit, such as 16 GB or 1 TB.",false);
            if(Set.of("Length","Maximum GPU length").contains(key))value=quantity(key,value,"(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(mm|cm)","Use a positive length with mm or cm, such as 300 mm.",true);
            if(Set.of("Wattage","Recommended PSU").contains(key))value=quantity(key,value,"(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(W)","Use a positive wattage, such as 650 W.",false);
            if(key.equals("Refresh rate"))value=quantity(key,value,"(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(Hz)","Use a positive refresh rate, such as 144 Hz.",false);
            }catch(ValidationException error){if(strict)throw error;}
            output.add(key+": "+value);
        }
        String result=output.toString();if(result.length()>1000)throw new ValidationException("Specifications must be at most 1,000 characters.");return result;
    }
    private static String quantity(String key,String value,String regex,String help,boolean length){
        var match=java.util.regex.Pattern.compile(regex).matcher(value);if(!match.matches())throw new ValidationException(key+": "+help);
        BigDecimal amount=new BigDecimal(match.group(1));if(amount.signum()<=0||amount.compareTo(new BigDecimal("1000000"))>0)throw new ValidationException(key+": "+help);
        String unit=match.group(2);unit=length?unit.toLowerCase(Locale.ROOT):unit.equalsIgnoreCase("Hz")?"Hz":unit.toUpperCase(Locale.ROOT);
        return amount.stripTrailingZeros().toPlainString()+" "+unit;
    }
}