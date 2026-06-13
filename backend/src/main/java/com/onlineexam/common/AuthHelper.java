package com.onlineexam.common;

import java.util.Map;
import java.util.Objects;

/**
 * 认证和权限检查工具类
 */
public final class AuthHelper {

  private AuthHelper() {}

  /**
   * 检查用户是否具有指定角色
   */
  public static boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  /**
   * 检查用户是否具有任一指定角色
   */
  public static boolean hasRole(Map<String, Object> user, String... roles) {
    if (user == null) return false;
    for (String role : roles) {
      if (isRole(user, role)) return true;
    }
    return false;
  }

  /**
   * 检查用户是否可以管理指定实体类型
   */
  public static boolean canManage(Map<String, Object> user, String entity) {
    if (isRole(user, "admin")) {
      return java.util.Set.of("users", "departments", "classes").contains(entity);
    }
    if (isRole(user, "teacher")) {
      return java.util.Set.of("questions", "papers", "exams").contains(entity);
    }
    return false;
  }

  /**
   * 从 Map 中安全获取字符串值
   */
  public static String str(Map<String, Object> map, String key) {
    return str(map == null ? null : map.get(key));
  }

  /**
   * 将对象转换为字符串
   */
  public static String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
