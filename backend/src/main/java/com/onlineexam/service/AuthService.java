package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务 - 处理登录和密码管理
 */
@Service
public class AuthService {

  private final StoreService storeService;
  private final SystemLogService systemLogService;
  private final JdbcTemplate jdbc;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  // Brute-force protection: track failed login attempts per username
  private final ConcurrentHashMap<String, List<Long>> loginAttempts = new ConcurrentHashMap<>();
  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final long LOCKOUT_WINDOW_MS = 1 * 60 * 1000; // 1 minute

  public AuthService(StoreService storeService, SystemLogService systemLogService, JdbcTemplate jdbc) {
    this.storeService = storeService;
    this.systemLogService = systemLogService;
    this.jdbc = jdbc;
  }

  /**
   * 用户登录
   */
  public ResponseEntity<?> login(Map<String, Object> body) {
    String inputUsername = str(body, "username");

    // Brute-force protection: check if account is temporarily locked
    if (isAccountLocked(inputUsername)) {
      return error(HttpStatus.TOO_MANY_REQUESTS, "登录尝试次数过多，请1分钟后再试。");
    }

    // Direct SQL query — only fetch the one user row needed, not the entire database
    List<Map<String, Object>> rows = jdbc.queryForList(
      "select id,role,username,password,name,department_id,class_id,major from user_account where username=? limit 1",
      inputUsername);
    if (rows.isEmpty() || !matchesPassword(str(body, "password"), str(rows.get(0), "password"))) {
      recordFailedAttempt(inputUsername);
      return error(HttpStatus.UNAUTHORIZED, "账号或密码错误，请检查后重试。");
    }
    Map<String, Object> matchedUser = rows.get(0);
    // Clear failed attempts on successful login
    loginAttempts.remove(inputUsername);
    if (needsPasswordUpgrade(str(matchedUser, "password"))) {
      matchedUser.put("password", hashPassword(str(body, "password")));
      storeService.saveRecord("users", matchedUser);
    }
    systemLogService.log(matchedUser, "login", str(matchedUser, "role") + ":" + str(matchedUser, "name"));
    return ResponseEntity.ok(mapOf("user", sanitizeUser(matchedUser)));
  }

  /**
   * Check if an account is temporarily locked due to too many failed attempts.
   */
  private boolean isAccountLocked(String username) {
    if (username == null || username.isBlank()) return false;
    long now = System.currentTimeMillis();
    List<Long> attempts = loginAttempts.get(username);
    if (attempts == null) return false;
    synchronized (attempts) {
      attempts.removeIf(t -> (now - t) > LOCKOUT_WINDOW_MS);
      return attempts.size() >= MAX_FAILED_ATTEMPTS;
    }
  }

  /**
   * Record a failed login attempt.
   */
  private void recordFailedAttempt(String username) {
    if (username == null || username.isBlank()) return;
    List<Long> attempts = loginAttempts.computeIfAbsent(username, k -> new ArrayList<>());
    synchronized (attempts) {
      attempts.add(System.currentTimeMillis());
    }
  }

  /**
   * 修改密码
   */
  public ResponseEntity<?> changePassword(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Not logged in.");
    if (!matchesPassword(str(body, "oldPassword"), str(user, "password"))) {
      return error(HttpStatus.BAD_REQUEST, "Old password is incorrect.");
    }
    String newPassword = str(body, "newPassword");
    if (newPassword.length() < 6) return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
    if (newPassword.length() > 100) return error(HttpStatus.BAD_REQUEST, "Password must be at most 100 characters.");
    user.put("password", hashPassword(newPassword));
    storeService.saveRecord("users", user);
    systemLogService.log(user, "change password", str(user, "username"));
    return ResponseEntity.ok(mapOf("success", true));
  }

  /**
   * 管理员重置密码
   */
  public ResponseEntity<?> resetPassword(String userId, Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "admin")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    Map<String, Object> target = find(store.users, str(body, "userId"));
    if (target == null) return error(HttpStatus.NOT_FOUND, "User not found.");
    String password = str(body, "newPassword").isBlank() ? "123456" : str(body, "newPassword");
    if (password.length() < 6) return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
    if (password.length() > 100) return error(HttpStatus.BAD_REQUEST, "Password must be at most 100 characters.");
    target.put("password", hashPassword(password));
    storeService.saveRecord("users", target);
    systemLogService.log(user, "reset password", str(target, "username"));
    return ResponseEntity.ok(mapOf("success", true));
  }

  public boolean matchesPassword(String rawPassword, String storedPassword) {
    if (storedPassword != null && storedPassword.startsWith("$2a$") || storedPassword != null && storedPassword.startsWith("$2b$")) {
      return passwordEncoder.matches(rawPassword, storedPassword);
    }
    return Objects.equals(rawPassword, storedPassword);
  }

  public String hashPassword(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  public boolean needsPasswordUpgrade(String storedPassword) {
    return storedPassword != null && !storedPassword.startsWith("$2a$") && !storedPassword.startsWith("$2b$");
  }

  private Map<String, Object> sanitizeUser(Map<String, Object> user) {
    Map<String, Object> safe = new LinkedHashMap<>(user);
    safe.remove("password");
    return safe;
  }

  private Map<String, Object> find(java.util.List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
