package com.onlineexam.controller;

import com.onlineexam.service.AuthService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器 - 处理登录和密码管理
 */
@RestController
@RequestMapping("/api")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
    return authService.login(body);
  }

  @PostMapping("/user/password")
  public ResponseEntity<?> changePassword(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @RequestBody Map<String, Object> body) {
    return authService.changePassword(userId, body);
  }

  @PostMapping("/admin/reset-password")
  public ResponseEntity<?> resetPassword(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                         @RequestBody Map<String, Object> body) {
    return authService.resetPassword(userId, body);
  }
}
