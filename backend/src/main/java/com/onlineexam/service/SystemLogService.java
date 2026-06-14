package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 系统日志服务 - 提供审计日志记录功能
 */
@Service
public class SystemLogService {

  private final StoreService storeService;

  public SystemLogService(StoreService storeService) {
    this.storeService = storeService;
  }

  /**
   * 记录系统日志
   *
   * @param user   操作用户（可为 null 表示系统操作）
   * @param action 操作类型
   * @param detail 操作详情
   */
  public void log(Map<String, Object> user, String action, String detail) {
    Map<String, Object> logEntry = new LinkedHashMap<>();
    logEntry.put("id", createId("log"));
    String actorId = user == null ? "system" : str(user, "id");
    String actorName = user == null ? "系统" : str(user, "name");
    logEntry.put("actorId", actorId);
    logEntry.put("actorName", actorName);
    logEntry.put("action", action);
    // detail: append user identity info if not already included
    String enrichedDetail = detail != null ? detail : "";
    if (user != null && !actorId.equals("system")) {
      String role = str(user, "role");
      String roleCn = switch (role) {
        case "admin" -> "管理员"; case "teacher" -> "教师"; case "student" -> "学生";
        default -> role;
      };
      if (!enrichedDetail.contains(actorId)) {
        enrichedDetail = "[" + roleCn + ":" + actorName + "(" + actorId + ")] " + enrichedDetail;
      }
    }
    logEntry.put("detail", enrichedDetail);
    logEntry.put("time", now());
    storeService.saveRecord("logs", logEntry);
  }

  private String createId(String prefix) {
    return prefix + "-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String now() {
    return Instant.now().toString();
  }
}
