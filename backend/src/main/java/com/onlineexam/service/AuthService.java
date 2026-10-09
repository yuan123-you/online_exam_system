package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
  private final SessionTokenService sessions;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
  private static final String PASSWORD_BYTE_LIMIT_MESSAGE =
    "Password must not exceed 72 UTF-8 bytes. Oversized legacy passwords cannot be safely authenticated; ask an administrator to reset the password.";

  // Brute-force protection: track failed login attempts per username
  private final ConcurrentHashMap<String, List<Long>> loginAttempts = new ConcurrentHashMap<>();
  private static final int MAX_FAILED_ATTEMPTS = 5;
  private static final long LOCKOUT_WINDOW_MS = 1 * 60 * 1000; // 1 minute

  public AuthService(StoreService storeService, SystemLogService systemLogService, JdbcTemplate jdbc) {
    this(storeService, systemLogService, jdbc, new SessionTokenService());
  }

  @Autowired
  public AuthService(StoreService storeService, SystemLogService systemLogService, JdbcTemplate jdbc, SessionTokenService sessions) {
    this.storeService = storeService;
    this.systemLogService = systemLogService;
    this.jdbc = jdbc;
    this.sessions = sessions == null ? new SessionTokenService() : sessions;
  }

  private static final String DEFAULT_REGISTRATION_CLASS = "classe-1779258228737-4b1d9a";
  private static final String DEFAULT_REGISTRATION_DEPARTMENT = "dept-1";

  /** Public options disclose organization names only, never accounts or examination data. */
  public ResponseEntity<?> registrationOptions() {
    return ResponseEntity.ok(mapOf(
      "defaultClassId", DEFAULT_REGISTRATION_CLASS,
      "departments", jdbc.queryForList("select id,name from department order by name"),
      "classes", jdbc.queryForList("select id,name,major,department_id as departmentId from class_info order by name")));
  }

  /** Self-service registration can create only a student, regardless of submitted role or ID. */
  public ResponseEntity<?> registerStudent(Map<String,Object> body) {
    String username=str(body,"username").trim();
    boolean defaultPassword = !body.containsKey("password");
    String password=defaultPassword ? "123456" : str(body,"password");
    String name=str(body,"name").trim();
    String departmentId=body.containsKey("departmentId") ? str(body,"departmentId").trim() : DEFAULT_REGISTRATION_DEPARTMENT;
    String classId=body.containsKey("classId") ? str(body,"classId").trim() : DEFAULT_REGISTRATION_CLASS;
    if(!username.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{3,31}")) return error(HttpStatus.BAD_REQUEST,"账号需为4至32位字母、数字或下划线，可包含点和短横线。");
    if((!defaultPassword && password.length()<8) || password.length()>64 || password.getBytes(StandardCharsets.UTF_8).length>72) return error(HttpStatus.BAD_REQUEST,"密码需为8至64个字符，且不能超过72个UTF-8字节。");
    if(name.isBlank() || name.length()>50 || name.matches(".*[<>\\p{Cntrl}].*")) return error(HttpStatus.BAD_REQUEST,"请填写有效用户名（最多50个字符）。");
    if(departmentId.isBlank() || classId.isBlank() || departmentId.length()>64 || classId.length()>64) return error(HttpStatus.BAD_REQUEST,"请选择所属院系和班级。");
    Integer existing=jdbc.queryForObject("select count(*) from user_account where username=?",Integer.class,username);
    if(existing!=null && existing>0) return error(HttpStatus.CONFLICT,"该账号已注册，请更换账号或直接登录。");
    List<Map<String,Object>> classes=jdbc.queryForList("select c.id,c.department_id,c.major from class_info c join department d on d.id=c.department_id where c.id=? and d.id=? limit 1",classId,departmentId);
    if(classes.isEmpty()) return error(HttpStatus.BAD_REQUEST,"所选班级不属于该院系，请重新选择。");
    String id="student_"+UUID.randomUUID().toString().replace("-","");
    String hashed=hashPassword(password);
    String major=str(classes.get(0),"major");
    try {
      int changed=jdbc.update("insert into user_account (id,role,username,password,name,department_id,class_id,major) values (?,?,?,?,?,?,?,?)",id,"student",username,hashed,name,departmentId,classId,major);
      if(changed!=1) return error(HttpStatus.INTERNAL_SERVER_ERROR,"注册暂未完成，请稍后重试。");
      storeService.invalidateCache();
    } catch(DuplicateKeyException exception) { return error(HttpStatus.CONFLICT,"该账号已注册，请更换账号或直接登录。"); }
    Map<String,Object> user=mapOf("id",id,"role","student","username",username,"name",name,"departmentId",departmentId,"classId",classId,"major",major);
    systemLogService.log(user,"register","student:"+name);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("user",user,"message","学生账号注册成功，请使用新账号登录。"));
  }

  /**
   * 用户登录
   */
  public ResponseEntity<?> login(Map<String, Object> body) {
    String inputUsername = str(body, "username");
    if (exceedsPasswordByteLimit(str(body, "password"))) {
      return error(HttpStatus.BAD_REQUEST, PASSWORD_BYTE_LIMIT_MESSAGE);
    }

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
    String storedPassword = str(matchedUser, "password");
    if (needsPasswordUpgrade(storedPassword)) {
      // Upgrade only the credential we authenticated; never overwrite a concurrent reset/change.
      String hashedPw = hashPassword(str(body, "password"));
      int changed = jdbc.update("update user_account set password=? where id=? and password=?",
        hashedPw, str(matchedUser, "id"), storedPassword);
      if (changed == 0) return error(HttpStatus.CONFLICT, "Account or password changed. Please retry login.");
      if (changed != 1) return error(HttpStatus.INTERNAL_SERVER_ERROR, "Password upgrade failed. Please retry login.");
    }
    // Clear failed attempts only after authentication and any required upgrade succeed.
    loginAttempts.remove(inputUsername);
    systemLogService.log(matchedUser, "login", str(matchedUser, "role") + ":" + str(matchedUser, "name"));
    return ResponseEntity.ok(mapOf("user", sanitizeUser(matchedUser), "sessionToken", sessions.issue(str(matchedUser,"id"))));
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
    if (exceedsPasswordByteLimit(str(body, "oldPassword"))) {
      return error(HttpStatus.BAD_REQUEST, PASSWORD_BYTE_LIMIT_MESSAGE);
    }
    // Query password directly from DB — store cache doesn't include the password column
    List<Map<String, Object>> rows = jdbc.queryForList(
      "select password from user_account where id=? limit 1", userId);
    if (rows.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "Not logged in.");
    String storedPassword = str(rows.get(0), "password");
    if (!matchesPassword(str(body, "oldPassword"), storedPassword)) {
      return error(HttpStatus.BAD_REQUEST, "Old password is incorrect.");
    }
    String newPassword = str(body, "newPassword");
    if (newPassword.length() < 6) return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
    if (newPassword.length() > 100) return error(HttpStatus.BAD_REQUEST, "Password must be at most 100 characters.");
    if (newPassword.isBlank()) return error(HttpStatus.BAD_REQUEST, "Password must not be blank.");
    if (exceedsPasswordByteLimit(newPassword)) return error(HttpStatus.BAD_REQUEST, PASSWORD_BYTE_LIMIT_MESSAGE);
    // Compare-and-set: the old-password proof is valid only for the credential we read.
    int changed = jdbc.update("update user_account set password=? where id=? and password=?",
      hashPassword(newPassword), userId, storedPassword);
    if (changed == 0) return error(HttpStatus.CONFLICT, "Account or password changed. Please retry.");
    if (changed != 1) return error(HttpStatus.INTERNAL_SERVER_ERROR, "Password change failed.");
    storeService.invalidateCache();
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
    if (!body.containsKey("newPassword")) {
      return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
    }
    String password = str(body, "newPassword");
    if (password.isEmpty()) {
      password = "123456";
    } else {
      if (password.length() < 6) return error(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters.");
      if (password.length() > 100) return error(HttpStatus.BAD_REQUEST, "Password must be at most 100 characters.");
      if (password.isBlank()) return error(HttpStatus.BAD_REQUEST, "Password must not be blank.");
      if (exceedsPasswordByteLimit(password)) return error(HttpStatus.BAD_REQUEST, PASSWORD_BYTE_LIMIT_MESSAGE);
    }
    // Update only the credential, never mutate a shared cached user or upsert its stale profile.
    int changed = jdbc.update("update user_account set password=? where id=?",
      hashPassword(password), str(target, "id"));
    if (changed == 0) return error(HttpStatus.NOT_FOUND, "User not found.");
    if (changed != 1) return error(HttpStatus.INTERNAL_SERVER_ERROR, "Password reset failed.");
    storeService.invalidateCache();
    systemLogService.log(user, "reset password", str(target, "username"));
    return ResponseEntity.ok(mapOf("success", true));
  }

  public boolean matchesPassword(String rawPassword, String storedPassword) {
    if (rawPassword == null || rawPassword.isBlank() || storedPassword == null || storedPassword.isBlank()) return false;
    if (exceedsPasswordByteLimit(rawPassword)) return false;
    if (storedPassword != null && storedPassword.startsWith("$2a$") || storedPassword != null && storedPassword.startsWith("$2b$")) {
      return passwordEncoder.matches(rawPassword, storedPassword);
    }
    return Objects.equals(rawPassword, storedPassword);
  }

  public String hashPassword(String rawPassword) {
    if (exceedsPasswordByteLimit(rawPassword)) throw new IllegalArgumentException(PASSWORD_BYTE_LIMIT_MESSAGE);
    return passwordEncoder.encode(rawPassword);
  }

  private boolean exceedsPasswordByteLimit(String password) {
    return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES;
  }

  public boolean needsPasswordUpgrade(String storedPassword) {
    return storedPassword != null && !storedPassword.startsWith("$2a$") && !storedPassword.startsWith("$2b$");
  }

  private Map<String, Object> sanitizeUser(Map<String, Object> user) {
    Map<String, Object> safe = new LinkedHashMap<>(user);
    safe.remove("password");
    // Normalize snake_case keys from JDBC to camelCase for frontend compatibility
    if (safe.containsKey("department_id")) {
      safe.putIfAbsent("departmentId", safe.get("department_id"));
      safe.remove("department_id");
    }
    if (safe.containsKey("class_id")) {
      safe.putIfAbsent("classId", safe.get("class_id"));
      safe.remove("class_id");
    }
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
