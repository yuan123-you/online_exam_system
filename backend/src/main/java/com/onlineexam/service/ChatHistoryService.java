package com.onlineexam.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.onlineexam.StoreService;

/**
 * 聊天历史服务 — 持久化 AI 对话会话和消息，最多保留 200 条会话/用户
 */
@Service
public class ChatHistoryService {

  private static final int MAX_CONVERSATIONS = 200;

  private final JdbcTemplate jdbc;
  private final StoreService storeService;

  public ChatHistoryService(JdbcTemplate jdbc, StoreService storeService) {
    this.jdbc = jdbc;
    this.storeService = storeService;
  }

  @PostConstruct
  void initTables() {
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS chat_conversation (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, title VARCHAR(200) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'student', created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, INDEX idx_chat_conv_user (user_id), INDEX idx_chat_conv_updated (updated_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      jdbc.execute("CREATE TABLE IF NOT EXISTS chat_message (id VARCHAR(64) PRIMARY KEY, conversation_id VARCHAR(64) NOT NULL, role VARCHAR(20) NOT NULL, content TEXT NOT NULL, reasoning TEXT, created_at DATETIME(3) NOT NULL, INDEX idx_chat_msg_conv (conversation_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception ignored) { }
  }

  /** 获取用户的会话列表（按更新时间降序，最多 200） */
  public ResponseEntity<?> listConversations(String userId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, title, role, created_at, updated_at FROM chat_conversation WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?",
        userId, MAX_CONVERSATIONS);

      List<Map<String, Object>> conversations = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        conversations.add(mapOf(
          "id", str(row.get("id")),
          "title", str(row.get("title")),
          "role", str(row.get("role")),
          "createdAt", str(row.get("created_at")),
          "updatedAt", str(row.get("updated_at"))
        ));
      }
      return ResponseEntity.ok(mapOf("conversations", conversations));
    } catch (Exception e) {
      // Table not yet created — return empty list
      return ResponseEntity.ok(mapOf("conversations", List.of()));
    }
  }

  /** 获取指定会话的所有消息 */
  public ResponseEntity<?> getMessages(String userId, String conversationId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      List<Map<String, Object>> conv = jdbc.queryForList(
        "SELECT id FROM chat_conversation WHERE id = ? AND user_id = ?", conversationId, userId);
      if (conv.isEmpty()) return error(HttpStatus.NOT_FOUND, "Conversation not found.");

      List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, role, content, reasoning, created_at FROM chat_message WHERE conversation_id = ? ORDER BY created_at ASC",
        conversationId);

      List<Map<String, Object>> messages = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", str(row.get("role")));
        msg.put("content", str(row.get("content")));
        String reasoning = str(row.get("reasoning"));
        if (!reasoning.isBlank()) msg.put("reasoning", reasoning);
        msg.put("createdAt", str(row.get("created_at")));
        messages.add(msg);
      }
      return ResponseEntity.ok(mapOf("messages", messages));
    } catch (Exception e) {
      return ResponseEntity.ok(mapOf("messages", List.of()));
    }
  }

  /** 新建会话 */
  public ResponseEntity<?> createConversation(String userId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    String title = str(body, "title");
    if (title.isBlank()) title = "新对话";
    if (title.length() > 200) title = title.substring(0, 200);

    String role = str(body, "role").isBlank() ? "student" : str(body, "role");
    String id = "conv-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
    String now = Instant.now().toString();

    try {
      jdbc.update(
        "INSERT INTO chat_conversation (id, user_id, title, role, created_at, updated_at) VALUES (?,?,?,?,?,?)",
        id, userId, title, role, now, now);
      enforceLimit(userId);
    } catch (Exception e) {
      // Table not yet created — return generated id anyway so frontend doesn't crash
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("title", title);
    result.put("role", role);
    result.put("createdAt", now);
    result.put("updatedAt", now);
    return ResponseEntity.ok(result);
  }

  /** 追加消息到会话（批量） */
  public ResponseEntity<?> appendMessages(String userId, String conversationId, Map<String, Object> body) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
    if (messages == null || messages.isEmpty()) return error(HttpStatus.BAD_REQUEST, "No messages provided.");

    try {
      // Verify ownership (skip if table doesn't exist)
      List<Map<String, Object>> conv = jdbc.queryForList(
        "SELECT id FROM chat_conversation WHERE id = ? AND user_id = ?", conversationId, userId);
      if (conv.isEmpty()) return ResponseEntity.ok(mapOf("saved", 0));

      String now = Instant.now().toString();
      int saved = 0;
      for (Map<String, Object> msg : messages) {
        String msgId = "msg-" + System.currentTimeMillis() + "-" + saved + "-" + Integer.toHexString((int) (Math.random() * 0xffff));
        jdbc.update(
          "INSERT INTO chat_message (id, conversation_id, role, content, reasoning, created_at) VALUES (?,?,?,?,?,?)",
          msgId, conversationId,
          str(msg, "role"),
          str(msg, "content"),
          nullableStr(msg, "reasoning"),
          now);
        saved++;
      }

      String firstUserContent = "";
      for (Map<String, Object> msg : messages) {
        if ("user".equals(str(msg, "role"))) {
          firstUserContent = str(msg, "content");
          break;
        }
      }
      String title = firstUserContent.isBlank() ? "新对话" : firstUserContent;
      if (title.length() > 50) title = title.substring(0, 50);
      jdbc.update("UPDATE chat_conversation SET updated_at = ?, title = ? WHERE id = ?", now, title, conversationId);

      return ResponseEntity.ok(mapOf("saved", saved));
    } catch (Exception e) {
      // Table not yet created — return success so frontend doesn't crash
      return ResponseEntity.ok(mapOf("saved", 0));
    }
  }

  /** 删除会话及其所有消息 */
  public ResponseEntity<?> deleteConversation(String userId, String conversationId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      int deleted = jdbc.update("DELETE FROM chat_conversation WHERE id = ? AND user_id = ?", conversationId, userId);
      if (deleted == 0) return error(HttpStatus.NOT_FOUND, "Conversation not found.");
      jdbc.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
    } catch (Exception e) {
      // Table not yet created
    }
    return ResponseEntity.ok(mapOf("deleted", true));
  }

  // ========== helpers ==========

  private void enforceLimit(String userId) {
    // Count conversations for user
    Integer count = jdbc.queryForObject(
      "SELECT COUNT(*) FROM chat_conversation WHERE user_id = ?", Integer.class, userId);
    if (count != null && count > MAX_CONVERSATIONS) {
      // Delete oldest conversations beyond limit
      int excess = count - MAX_CONVERSATIONS;
      List<String> toDelete = jdbc.queryForList(
        "SELECT id FROM chat_conversation WHERE user_id = ? ORDER BY updated_at ASC LIMIT ?",
        String.class, userId, excess);
      for (String id : toDelete) {
        jdbc.update("DELETE FROM chat_message WHERE conversation_id = ?", id);
        jdbc.update("DELETE FROM chat_conversation WHERE id = ?", id);
      }
    }
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private String str(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private String nullableStr(Map<String, Object> map, String key) {
    String v = str(map, key);
    return v.isBlank() ? null : v;
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
