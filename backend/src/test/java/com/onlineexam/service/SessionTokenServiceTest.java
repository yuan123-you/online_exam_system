package com.onlineexam.service;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SessionTokenServiceTest {
  static final String SECRET="unit-test-session-secret-with-at-least-thirty-two-bytes";
  static final Instant NOW=Instant.parse("2026-10-08T00:00:00Z");
  @Test void authenticatesOnlyIssuedUser() {
    var s=new SessionTokenService(SECRET,Clock.fixed(NOW,ZoneOffset.UTC));
    String token=s.issue("student_1");assertTrue(s.validate(token,"student_1"));assertFalse(s.validate(token,"admin_1"));
  }
  @Test void rejectsChangedSignatureAndMalformedTokens() {
    var s=new SessionTokenService(SECRET,Clock.fixed(NOW,ZoneOffset.UTC));String token=s.issue("u1");
    assertFalse(s.validate(token+"x","u1"));assertFalse(s.validate("u1","u1"));assertFalse(s.validate(null,"u1"));
  }
  @Test void expiresSessionAndDoesNotAcceptDifferentSecret() {
    var s=new SessionTokenService(SECRET,Clock.fixed(NOW,ZoneOffset.UTC));String token=s.issue("u1");
    assertFalse(new SessionTokenService(SECRET,Clock.fixed(NOW.plusSeconds(13*3600),ZoneOffset.UTC)).validate(token,"u1"));
    assertFalse(new SessionTokenService(SECRET+"different",Clock.fixed(NOW,ZoneOffset.UTC)).validate(token,"u1"));
  }
}
