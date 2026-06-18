package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final StoreService storeService;
  private final JdbcTemplate jdbc;

  public NotificationService(StoreService storeService, JdbcTemplate jdbc) {
    this.storeService = storeService;
    this.jdbc = jdbc;
  }

  /**
   * 获取用户的通知列表（最近 50 条）
   */
  public List<Map<String, Object>> getUserNotifications(String userId) {
    Store store;
    try {
      store = storeService.readStore();
    } catch (Exception e) {
      return List.of();
    }
    Map<String, Object> user = findById(store.users, userId);
    if (user == null) return List.of();

    String role = str(user, "role");
    String classId = str(user, "classId");
    String departmentId = str(user, "departmentId");

    // 获取该用户已读的通知ID集合
    Set<String> readIds = getReadNotificationIds(userId);

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
            if (!targetClassId.isEmpty()) {
              return Objects.equals(targetClassId, classId);
            }
            // 如果是教师，检查学院
            String targetDeptId = str(n, "targetDepartmentId");
            if (!targetDeptId.isEmpty()) {
              return Objects.equals(targetDeptId, departmentId);
            }
            // 无班级/学院限制，该角色所有人可见
            return true;
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
          // 添加已读状态（基于 notification_read 表）
          item.put("isRead", readIds.contains(str(item, "id")));
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
  @Transactional
  public boolean markAsRead(String notificationId, String userId) {
    try {
      // 使用 INSERT IGNORE 避免重复插入
      jdbc.update(
        "INSERT IGNORE INTO notification_read (id, notification_id, user_id, read_at) VALUES (?, ?, ?, ?)",
        "nr-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff)),
        notificationId, userId, Instant.now().toString()
      );
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 标记所有通知为已读
   */
  @Transactional
  public int markAllAsRead(String userId) {
    List<Map<String, Object>> notifications = getUserNotifications(userId);
    int count = 0;
    for (Map<String, Object> notif : notifications) {
      if (!asBool(notif.get("isRead"))) {
        if (markAsRead(str(notif, "id"), userId)) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * 创建通知
   */
  @Transactional
  public Map<String, Object> createNotification(String senderId, String title, String content,
                                                  String type, String targetRole, String targetClassId,
                                                  String targetDepartmentId) {
    Map<String, Object> notif = new LinkedHashMap<>();
    notif.put("id", UUID.randomUUID().toString());
    notif.put("senderId", senderId);
    notif.put("title", title);
    notif.put("content", content);
    notif.put("type", type != null ? type : "general");
    notif.put("targetRole", targetRole);
    notif.put("targetClassId", targetClassId != null ? targetClassId : "");
    notif.put("targetDepartmentId", targetDepartmentId != null ? targetDepartmentId : "");
    notif.put("targetUserId", "");
    notif.put("createdAt", Instant.now().toString());
    storeService.saveRecord("notifications", notif);
    return notif;
  }

  /**
   * 获取用户已读的通知ID集合
   */
  private Set<String> getReadNotificationIds(String userId) {
    try {
      List<String> ids = jdbc.queryForList(
        "SELECT notification_id FROM notification_read WHERE user_id = ?", String.class, userId
      );
      return new HashSet<>(ids);
    } catch (Exception e) {
      return Set.of();
    }
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
