package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private final StoreService storeService;

  public NotificationService(StoreService storeService) {
    this.storeService = storeService;
  }

  /**
   * 获取用户的通知列表（最近 50 条）
   */
  public List<Map<String, Object>> getUserNotifications(String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = findById(store.users, userId);
    if (user == null) return List.of();

    String role = str(user, "role");
    String classId = str(user, "classId");

    return store.notifications.stream()
        .filter(n -> {
          // 直接发给该用户的
          if (Objects.equals(str(n, "targetUserId"), userId)) return true;
          // 发给所有该角色的
          String targetRole = str(n, "targetRole");
          if ("all".equals(targetRole)) return true;
          if (Objects.equals(targetRole, role)) {
            // 如果是学生，检查班级
            String targetClassId = str(n, "targetClassId");
            if (targetClassId.isEmpty()) return true; // 所有班级
            return Objects.equals(targetClassId, classId);
          }
          return false;
        })
        .sorted(Comparator.comparing(n -> str((Map<String, Object>) n, "createdAt"), Comparator.reverseOrder()))
        .limit(50)
        .map(n -> {
          Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) n);
          // 添加发送者名称
          Map<String, Object> sender = findById(store.users, str(item, "senderId"));
          item.put("senderName", sender != null ? str(sender, "name") : "系统");
          return item;
        })
        .toList();
  }

  /**
   * 获取未读通知数量
   */
  public long getUnreadCount(String userId) {
    return getUserNotifications(userId).stream()
        .filter(n -> !asBool(n.get("isRead")))
        .count();
  }

  /**
   * 标记通知为已读
   */
  public boolean markAsRead(String notificationId, String userId) {
    Store store = storeService.readStore();
    for (Map<String, Object> notif : store.notifications) {
      if (Objects.equals(str(notif, "id"), notificationId)) {
        notif.put("isRead", true);
        notif.put("readAt", Instant.now().toString());
        storeService.saveRecord("notifications", notif);
        return true;
      }
    }
    return false;
  }

  /**
   * 标记所有通知为已读
   */
  public int markAllAsRead(String userId) {
    List<Map<String, Object>> notifications = getUserNotifications(userId);
    int count = 0;
    for (Map<String, Object> notif : notifications) {
      if (!asBool(notif.get("isRead"))) {
        String id = str(notif, "id");
        Store store = storeService.readStore();
        for (Map<String, Object> n : store.notifications) {
          if (Objects.equals(str(n, "id"), id)) {
            n.put("isRead", true);
            n.put("readAt", Instant.now().toString());
            storeService.saveRecord("notifications", n);
            count++;
            break;
          }
        }
      }
    }
    return count;
  }

  /**
   * 创建通知
   */
  public Map<String, Object> createNotification(String senderId, String title, String content,
                                                  String type, String targetRole, String targetClassId) {
    Store store = storeService.readStore();
    Map<String, Object> notif = new LinkedHashMap<>();
    notif.put("id", UUID.randomUUID().toString());
    notif.put("senderId", senderId);
    notif.put("title", title);
    notif.put("content", content);
    notif.put("type", type != null ? type : "general");
    notif.put("targetRole", targetRole);
    notif.put("targetClassId", targetClassId != null ? targetClassId : "");
    notif.put("targetUserId", "");
    notif.put("isRead", false);
    notif.put("createdAt", Instant.now().toString());
    storeService.saveRecord("notifications", notif);
    return notif;
  }

  private Map<String, Object> findById(List<Map<String, Object>> rows, String id) {
    if (id == null || id.isEmpty()) return null;
    return rows.stream().filter(r -> Objects.equals(str(r, "id"), id)).findFirst().orElse(null);
  }

  private boolean asBool(Object value) {
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    return value != null && Boolean.parseBoolean(String.valueOf(value));
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }
}
