package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.NotificationService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class NotificationController {

  private final StoreService storeService;
  private final NotificationService notificationService;

  public NotificationController(StoreService storeService, NotificationService notificationService) {
    this.storeService = storeService;
    this.notificationService = notificationService;
  }

  @GetMapping("/notifications")
  public ResponseEntity<?> getNotifications(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    if (userId == null) return error(HttpStatus.UNAUTHORIZED, "Unauthorized.");
    List<Map<String, Object>> notifications = notificationService.getUserNotifications(userId);
    long unreadCount = notificationService.getUnreadCount(userId);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("notifications", notifications);
    result.put("unreadCount", unreadCount);
    return ResponseEntity.ok(result);
  }

  @PostMapping("/notifications/{id}/read")
  public ResponseEntity<?> markAsRead(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @PathVariable String id) {
    if (userId == null) return error(HttpStatus.UNAUTHORIZED, "Unauthorized.");
    notificationService.markAsRead(id, userId);
    return ResponseEntity.ok(Map.of("success", true));
  }

  @PostMapping("/notifications/read-all")
  public ResponseEntity<?> markAllAsRead(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    if (userId == null) return error(HttpStatus.UNAUTHORIZED, "Unauthorized.");
    int count = notificationService.markAllAsRead(userId);
    return ResponseEntity.ok(Map.of("success", true, "count", count));
  }

  @PostMapping("/notifications")
  public ResponseEntity<?> createNotification(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                               @RequestBody Map<String, Object> body) {
    Store store = storeService.readStore();
    Map<String, Object> user = store.users.stream()
        .filter(u -> Objects.equals(String.valueOf(u.get("id")), userId)).findFirst().orElse(null);
    if (user == null) return error(HttpStatus.UNAUTHORIZED, "Unauthorized.");
    String role = String.valueOf(user.getOrDefault("role", ""));
    if (!"teacher".equals(role) && !"admin".equals(role)) return error(HttpStatus.FORBIDDEN, "Forbidden.");

    String title = String.valueOf(body.getOrDefault("title", ""));
    String content = String.valueOf(body.getOrDefault("content", ""));
    String type = String.valueOf(body.getOrDefault("type", "general"));
    String targetRole = String.valueOf(body.getOrDefault("targetRole", "student"));
    String targetClassId = body.get("targetClassId") != null ? String.valueOf(body.get("targetClassId")) : null;

    if (title.isBlank() || content.isBlank()) return error(HttpStatus.BAD_REQUEST, "标题和内容不能为空。");

    Map<String, Object> notif = notificationService.createNotification(userId, title, content, type, targetRole, targetClassId);
    return ResponseEntity.ok(Map.of("notification", notif));
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message));
  }
}
