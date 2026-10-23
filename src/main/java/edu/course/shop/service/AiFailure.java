package edu.course.shop.service;
/** Actionable failures without credentials or request bodies. */
public final class AiFailure extends IllegalStateException {
 public enum Kind { TIMEOUT, CONNECTION, SERVICE, RATE_LIMIT, DAILY_QUOTA, ACCESS, MODEL, REQUEST, RESPONSE, COOLDOWN }
 private final Kind kind;private final int status;private final long retryAfterSeconds;
 AiFailure(Kind kind,int status,long delay,String message){super(message);this.kind=kind;this.status=status;this.retryAfterSeconds=delay;}
 public Kind kind(){return kind;}public int status(){return status;}public long retryAfterSeconds(){return retryAfterSeconds;}
}
