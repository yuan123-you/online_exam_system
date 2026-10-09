package com.onlineexam.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Signed, expiring sessions: a public user ID is never an authentication credential. */
@Component
public class SessionTokenService {
  private static final long LIFETIME_SECONDS=12*60*60;
  private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
  private final byte[] secret;
  private final Clock clock;
  private final SecureRandom random=new SecureRandom();

  public SessionTokenService() { this(configuredSecret(),Clock.systemUTC()); }
  SessionTokenService(String secret,Clock clock) {
    if(secret==null || secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("Session secret must contain at least 32 bytes");
    this.secret=secret.getBytes(StandardCharsets.UTF_8);this.clock=clock;
  }
  private static String configuredSecret() {
    String configured=System.getenv("AUTH_SESSION_SECRET");
    if(configured!=null && !configured.isBlank()) return configured;
    byte[] key=new byte[32];new SecureRandom().nextBytes(key);
    return ENCODER.encodeToString(key);
  }
  public String issue(String userId) {
    if(userId==null || userId.isBlank() || userId.contains("|")) throw new IllegalArgumentException("Invalid session user");
    byte[] nonce=new byte[16];random.nextBytes(nonce);
    String data=userId+"|"+(clock.instant().getEpochSecond()+LIFETIME_SECONDS)+"|"+ENCODER.encodeToString(nonce);
    String payload=ENCODER.encodeToString(data.getBytes(StandardCharsets.UTF_8));
    return payload+"."+ENCODER.encodeToString(sign(payload));
  }
  public boolean validate(String token,String expectedUserId) {
    if(token==null || expectedUserId==null || token.length()>2048) return false;
    try {
      String[] parts=token.split("\\.",-1);
      if(parts.length!=2 || !MessageDigest.isEqual(sign(parts[0]),Base64.getUrlDecoder().decode(parts[1]))) return false;
      String[] data=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8).split("\\|",-1);
      return data.length==3 && expectedUserId.equals(data[0]) && !data[2].isEmpty() && Long.parseLong(data[1])>clock.instant().getEpochSecond();
    } catch(IllegalArgumentException exception) { return false; }
  }
  private byte[] sign(String payload) {
    try {
      Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch(java.security.GeneralSecurityException exception) { throw new IllegalStateException("Session signing unavailable",exception); }
  }
}
