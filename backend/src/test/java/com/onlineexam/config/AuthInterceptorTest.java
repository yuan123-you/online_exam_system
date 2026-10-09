package com.onlineexam.config;
import com.onlineexam.service.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AuthInterceptorTest {
 @Test void publicUserIdWithoutSessionCannotAuthenticate(){
  var jdbc=mock(JdbcTemplate.class);var session=new SessionTokenService();var guard=new AuthInterceptor(jdbc,session);
  var request=new MockHttpServletRequest();request.addHeader("X-User-Id","admin");var response=new MockHttpServletResponse();
  assertFalse(guard.preHandle(request,response,new Object()));assertEquals(401,response.getStatus());verifyNoInteractions(jdbc);
 }
 @Test void studentSessionCannotImpersonateAdministrator(){
  var jdbc=mock(JdbcTemplate.class);var session=new SessionTokenService();var guard=new AuthInterceptor(jdbc,session);
  var request=new MockHttpServletRequest();request.addHeader("X-User-Id","admin");request.addHeader("X-Session-Token",session.issue("student"));
  assertFalse(guard.preHandle(request,new MockHttpServletResponse(),new Object()));verifyNoInteractions(jdbc);
 }
 @Test void validSessionCanUseOnlyItsExistingAccount(){
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject("select count(*) from user_account where id=?",Integer.class,"student")).thenReturn(1);
  var session=new SessionTokenService();var guard=new AuthInterceptor(jdbc,session);
  var request=new MockHttpServletRequest();request.addHeader("X-User-Id","student");request.addHeader("X-Session-Token",session.issue("student"));
  assertTrue(guard.preHandle(request,new MockHttpServletResponse(),new Object()));assertEquals("student",request.getAttribute("currentUserId"));
 }
}
