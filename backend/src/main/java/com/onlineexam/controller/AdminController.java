package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.AuthService;
import com.onlineexam.service.EntityCrudService;
import com.onlineexam.service.SystemLogService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员控制器 - 处理用户、部门、班级的 CRUD 和批量导入
 */
@RestController
@RequestMapping("/api")
public class AdminController {

  private final StoreService storeService;
  private final EntityCrudService entityCrudService;
  private final AuthService authService;
  private final SystemLogService systemLogService;

  public AdminController(StoreService storeService, EntityCrudService entityCrudService,
                         AuthService authService, SystemLogService systemLogService) {
    this.storeService = storeService;
    this.entityCrudService = entityCrudService;
    this.authService = authService;
    this.systemLogService = systemLogService;
  }

  @PostMapping("/entities")
  public ResponseEntity<?> createEntity(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                        @RequestBody Map<String, Object> body) {
    return entityCrudService.createEntity(userId, body);
  }

  @PostMapping("/users/batch-import")
  public ResponseEntity<?> importUsers(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "admin")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    List<Map<String, Object>> created = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    for (int i = 0; i < asList(body.get("records")).size(); i++) {
      Map<String, Object> record = new LinkedHashMap<>(asMap(asList(body.get("records")).get(i)));
      record.put("id", createId(str(record, "role").isBlank() ? "user" : str(record, "role")));
      String rawPw = str(record, "password").isBlank() ? "123456" : str(record, "password");
      record.put("password", authService.hashPassword(rawPw));
      String validation = entityCrudService.validate(store, "users", record, null);
      if (!validation.isBlank()) {
        errors.add(mapOf("index", i, "title", str(record, "username"), "message", validation));
      } else {
        storeService.saveRecord("users", record);
        store.users = new ArrayList<>(store.users);
        store.users.add(record);
        created.add(record);
      }
    }
    if (!created.isEmpty()) systemLogService.log(user, "batch import users", String.valueOf(created.size()));
    return ResponseEntity.ok(mapOf("importedCount", created.size(), "failedCount", errors.size(), "created", created, "errors", errors));
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object raw) {
    return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private List<Object> asList(Object raw) {
    return raw instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }

  private String createId(String prefix) {
    return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
  }
}
