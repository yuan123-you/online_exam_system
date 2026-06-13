package com.onlineexam.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 响应构建工具类
 */
public final class ResponseHelper {

  private ResponseHelper() {}

  /**
   * 构建键值对 Map（用于响应体）
   */
  public static Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return map;
  }

  /**
   * 构建错误响应
   */
  public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  /**
   * 构建成功响应
   */
  public static ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
    return ResponseEntity.ok(body);
  }
}
