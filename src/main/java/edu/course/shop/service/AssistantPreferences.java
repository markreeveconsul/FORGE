package edu.course.shop.service;

import edu.course.shop.model.Category;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicitly saved, non-sensitive shopping preferences stored locally for this OS account. */
public final class AssistantPreferences {
    public record Profile(Category category, BigDecimal budget) {
        public boolean empty(){return category==null&&budget==null;}
    }
    private final Path file;
    public AssistantPreferences(){this(Path.of(System.getProperty("user.home"),".forge","assistant-preferences.properties"));}
    AssistantPreferences(Path file){this.file=file;}
    public synchronized Profile load(long userId){
        Properties values=read();String prefix="user."+Math.max(0,userId)+".";
        Category category=null;try{String value=values.getProperty(prefix+"category","");if(!value.isBlank())category=Category.valueOf(value);}catch(IllegalArgumentException ignored){}
        BigDecimal budget=null;try{String value=values.getProperty(prefix+"budget","");if(!value.isBlank())budget=new BigDecimal(value);}catch(NumberFormatException ignored){}
        return new Profile(category,budget);
    }
    public synchronized boolean hasGeminiConsent(long userId){
        return Boolean.parseBoolean(read().getProperty("user."+Math.max(0,userId)+".geminiConsent","false"));
    }
    public synchronized void rememberGeminiConsent(long userId){
        Properties values=read();values.setProperty("user."+Math.max(0,userId)+".geminiConsent","true");write(values);
    }
    public synchronized void forgetGeminiConsent(long userId){
        Properties values=read();values.remove("user."+Math.max(0,userId)+".geminiConsent");write(values);
    }
    public synchronized Profile parseAndSave(long userId,String request){
        String q=request.toLowerCase(Locale.ROOT);
        if(!q.matches("(?s).*\\b(remember|save|keep)\\b.*\\b(prefer|preference|budget|looking for)\\b.*"))return null;
        Category category=category(q);BigDecimal budget=parseBudget(q);
        if(category==null&&budget==null)return null;
        Profile old=load(userId);Profile next=new Profile(category==null?old.category():category,budget==null?old.budget():budget);
        Properties values=read();String prefix="user."+Math.max(0,userId)+".";
        if(next.category()==null)values.remove(prefix+"category");else values.setProperty(prefix+"category",next.category().name());
        if(next.budget()==null)values.remove(prefix+"budget");else values.setProperty(prefix+"budget",next.budget().toPlainString());
        write(values);
        return next;
    }
    public synchronized boolean clear(long userId){
        Properties values=read();String prefix="user."+Math.max(0,userId)+".";
        values.remove(prefix+"category");values.remove(prefix+"budget");write(values);return true;
    }
    public static String apply(String question,Profile profile){
        if(profile==null||profile.empty())return question;
        String q=question.toLowerCase(Locale.ROOT);StringBuilder suffix=new StringBuilder();
        if(profile.category()!=null&&!mentionsCategory(q))suffix.append(" for ").append(profile.category().label());
        if(profile.budget()!=null&&!hasBudget(q))suffix.append(" under $").append(profile.budget().stripTrailingZeros().toPlainString());
        return question+suffix;
    }
    private static Category category(String q){
        if(q.matches("(?s).*\\b(laptop|laptops|notebook|notebooks)\\b.*"))return Category.LAPTOPS;
        if(q.matches("(?s).*\\b(phone|phones|tablet|tablets)\\b.*"))return Category.PHONES_TABLETS;
        if(q.matches("(?s).*\\b(gpu|gpus|graphics|cpu|processor|ram|motherboard|psu|ssd)\\b.*"))return Category.PC_PARTS;
        if(q.matches("(?s).*\\b(keyboard|keyboards|mouse|mice|headset|headphones|monitor)\\b.*"))return Category.PERIPHERALS;
        if(q.matches("(?s).*\\b(pc|desktop|desktops)\\b.*"))return Category.PC_SETS;
        return null;
    }
    private static boolean mentionsCategory(String q){return category(q)!=null;}
    private static boolean hasBudget(String q){return parseBudget(q)!=null;}
    private static BigDecimal parseBudget(String q){
        Matcher m=Pattern.compile("(?:under|below|less than|up to|maximum|max|budget(?: of| is)?|within)\\s*\\$?\\s*(\\d[\\d,]*(?:\\.\\d{1,2})?)\\s*(k)?\\b").matcher(q);
        if(!m.find())return null;
        BigDecimal amount=new BigDecimal(m.group(1).replace(",",""));return m.group(2)==null?amount:amount.multiply(new BigDecimal("1000"));
    }
    private Properties read(){
        Properties values=new Properties();if(!Files.isRegularFile(file))return values;
        try(InputStream in=Files.newInputStream(file)){values.load(in);return values;}
        catch(IOException e){throw new IllegalStateException("Could not read saved shopping preferences from this device.");}
    }
    private void write(Properties values){
        try{
            Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");
            try(OutputStream out=Files.newOutputStream(temp)){values.store(out,"FORGE shopping preferences - stored locally");}
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException e){throw new IllegalStateException("Could not save shopping preferences on this device.");}
    }
}
