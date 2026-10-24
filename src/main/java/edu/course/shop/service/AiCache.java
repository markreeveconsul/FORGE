package edu.course.shop.service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;
/** Bounded session-only LRU cache with expiring digest keys. */
final class AiCache<T> {
 private record Entry<T>(T value,long expires,int bytes){}
 private final LinkedHashMap<String,Entry<T>> entries=new LinkedHashMap<>(16,.75f,true);
 private final int maxEntries,maxBytes;private final long ttl;private final LongSupplier clock;private int bytes;
 AiCache(int count,int bytes,long ttl,LongSupplier clock){maxEntries=count;maxBytes=bytes;this.ttl=ttl;this.clock=clock;}
 synchronized T get(String key){var e=entries.get(key);if(e==null)return null;if(clock.getAsLong()-e.expires()>=0){entries.remove(key);bytes-=e.bytes();return null;}return e.value();}
 synchronized void put(String key,T value,int size){if(size>maxBytes)return;var old=entries.remove(key);if(old!=null)bytes-=old.bytes();entries.put(key,new Entry<>(value,clock.getAsLong()+ttl,size));bytes+=size;while(entries.size()>maxEntries||bytes>maxBytes){var i=entries.entrySet().iterator();bytes-=i.next().getValue().bytes();i.remove();}}
 synchronized void clear(){entries.clear();bytes=0;}
 static String digest(String text){return digest(text.getBytes(StandardCharsets.UTF_8));}
 static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
}
