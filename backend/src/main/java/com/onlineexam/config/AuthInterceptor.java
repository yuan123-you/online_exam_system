package com.onlineexam.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器 - 验证请求中的 X-User-Id 头
 * 排除路径: /api/login, /api/health
 *
 * Security: validates that the X-User-Id corresponds to a real user in the
 * database, preventing trivial header forgery.  A short-lived cache (30 s)
 * avoids a full store read on every request.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  private final JdbcTemplate jdbc;

  /** Cache: userId -> expiry timestamp (ms).  Entries are valid for 30 seconds. */
  private final ConcurrentHashMap<String, Long> validUserCache = new ConcurrentHashMap<>();
  private static final long CACHE_TTL_MS = 30_000; // 30 seconds

  public AuthInterceptor(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    // OPTIONS 请求直接放行（CORS 预检）
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String userId = request.getHeader("X-User-Id");
    if (userId == null || userId.isBlank()) {
      response.setStatus(401);
      return false;
    }

    // Validate user exists in database (with short-lived cache for performance)
    if (!isUserValid(userId)) {
      response.setStatus(401);
      return false;
    }

    // 将 userId 存储在请求属性中供控制器使用
    request.setAttribute("currentUserId", userId);
    return true;
  }

  /**
   * Check if a user ID corresponds to a real user.
   * Uses an in-memory cache to avoid a full DB scan on every request.
   */
  private boolean isUserValid(String userId) {
    long now = System.currentTimeMillis();
    Long expiry = validUserCache.get(userId);
    if (expiry != null && expiry > now) {
      return true; // still cached and valid
    }
    if (expiry != null) {
      validUserCache.remove(userId);
    }
    // Direct COUNT query — one indexed lookup, not a full table scan
    Integer count = jdbc.queryForObject(
      "select count(*) from user_account where id=?", Integer.class, userId);
    boolean exists = count != null && count > 0;
    if (exists) {
      validUserCache.put(userId, now + CACHE_TTL_MS);
    }
    return exists;
  }
}
