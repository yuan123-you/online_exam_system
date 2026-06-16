package com.onlineexam.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
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
      jdbc.execute("CREATE TABLE IF NOT EXISTS chat_conversation (id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, title VARCHAR(200) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'student', session_type VARCHAR(20) NOT NULL DEFAULT 'chat', created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, INDEX idx_chat_conv_user (user_id), INDEX idx_chat_conv_updated (updated_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      // Add session_type column if upgrading from older schema (IF NOT EXISTS unsupported in MySQL <8.0.29)
      try { jdbc.execute("ALTER TABLE chat_conversation ADD COLUMN session_type VARCHAR(20) NOT NULL DEFAULT 'chat'"); } catch (Exception ignored) {}
      jdbc.execute("CREATE TABLE IF NOT EXISTS chat_message (id VARCHAR(64) PRIMARY KEY, conversation_id VARCHAR(64) NOT NULL, role VARCHAR(20) NOT NULL, content TEXT NOT NULL, reasoning TEXT, created_at DATETIME(3) NOT NULL, INDEX idx_chat_msg_conv (conversation_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception e) {
      log.error("Failed to initialize tables: {}", e.getMessage());
    }
  }

  /** 获取用户的会话列表（按更新时间降序，最多 200） */
  public ResponseEntity<?> listConversations(String userId) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");

    try {
      List<Map<String, Object>> rows;
      try {
        rows = jdbc.queryForList(
          "SELECT id, title, role, session_type, created_at, updated_at FROM chat_conversation WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?",
          userId, MAX_CONVERSATIONS);
      } catch (Exception e) {
        // session_type column may not exist yet — fall back
        rows = jdbc.queryForList(
          "SELECT id, title, role, created_at, updated_at FROM chat_conversation WHERE user_id = ? ORDER BY updated_at DESC LIMIT ?",
          userId, MAX_CONVERSATIONS);
      }

      List<Map<String, Object>> conversations = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        conversations.add(mapOf(
          "id", str(row.get("id")),
          "title", str(row.get("title")),
          "role", str(row.get("role")),
          "sessionType", str(row.getOrDefault("session_type", "chat")),
          "createdAt", str(row.get("created_at")),
          "updatedAt", str(row.get("updated_at"))
        ));
      }
      return ResponseEntity.ok(mapOf("conversations", conversations));
    } catch (Exception e) {
      log.error("Failed to list conversations: {}", e.getMessage());
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
        msg.put("id", str(row.get("id")));
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
    String sessionType = str(body, "sessionType").isBlank() ? "chat" : str(body, "sessionType");
    String id = "conv-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xffffff));
    String nowStr = now();

    try {
      try {
        jdbc.update(
          "INSERT INTO chat_conversation (id, user_id, title, role, session_type, created_at, updated_at) VALUES (?,?,?,?,?,?,?)",
          id, userId, title, role, sessionType, nowStr, nowStr);
      } catch (Exception colEx) {
        // session_type column may not exist yet — fall back
        jdbc.update(
          "INSERT INTO chat_conversation (id, user_id, title, role, created_at, updated_at) VALUES (?,?,?,?,?,?)",
          id, userId, title, role, nowStr, nowStr);
      }
      enforceLimit(userId);
    } catch (Exception e) {
      log.error("Failed to insert conversation: {}", e.getMessage());
      return error(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create conversation.");
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", id);
    result.put("title", title);
    result.put("role", role);
    result.put("sessionType", sessionType);
    result.put("createdAt", nowStr);
    result.put("updatedAt", nowStr);
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

      String nowStr = now();
      int saved = 0;
      for (Map<String, Object> msg : messages) {
        String msgId = "msg-" + System.currentTimeMillis() + "-" + saved + "-" + Integer.toHexString((int) (Math.random() * 0xffff));
        jdbc.update(
          "INSERT INTO chat_message (id, conversation_id, role, content, reasoning, created_at) VALUES (?,?,?,?,?,?)",
          msgId, conversationId,
          str(msg, "role"),
          str(msg, "content"),
          nullableStr(msg, "reasoning"),
          nowStr);
        saved++;
      }

      // Update session_type if provided (column may not exist yet)
      String sessionType = str(body, "sessionType");
      if (!sessionType.isBlank()) {
        try {
          jdbc.update("UPDATE chat_conversation SET session_type = ? WHERE id = ?", sessionType, conversationId);
        } catch (Exception ignored) {
          // Column may not exist — non-critical, ignore
        }
      }

      // Smart title generation: only update title if it's still the default "新对话"
      // This preserves the original topic title across multiple exchanges
      String currentTitle = jdbc.queryForObject(
        "SELECT title FROM chat_conversation WHERE id = ?", String.class, conversationId);
      if (currentTitle == null || currentTitle.equals("新对话")) {
        String firstUserContent = "";
        for (Map<String, Object> msg : messages) {
          if ("user".equals(str(msg, "role"))) {
            firstUserContent = str(msg, "content");
            break;
          }
        }
        String title = generateSmartTitle(firstUserContent);
        jdbc.update("UPDATE chat_conversation SET updated_at = ?, title = ? WHERE id = ?", nowStr, title, conversationId);
      } else {
        // Just update the timestamp
        jdbc.update("UPDATE chat_conversation SET updated_at = ? WHERE id = ?", nowStr, conversationId);
      }

      return ResponseEntity.ok(mapOf("saved", saved));
    } catch (Exception e) {
      log.error("Failed to append messages: {}", e.getMessage());
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

  /** 搜索历史对话 — 按关键词匹配标题和消息内容 */
  public ResponseEntity<?> searchConversations(String userId, String keyword) {
    if (userId == null || userId.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Not authenticated.");
    if (keyword == null || keyword.isBlank()) return listConversations(userId);

    try {
      String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      String pattern = "%" + escaped + "%";
      // Search in both conversation titles and message content
      List<Map<String, Object>> rows;
      try {
        rows = jdbc.queryForList(
          "SELECT DISTINCT c.id, c.title, c.role, c.session_type, c.created_at, c.updated_at " +
          "FROM chat_conversation c " +
          "LEFT JOIN chat_message m ON m.conversation_id = c.id " +
          "WHERE c.user_id = ? AND (c.title LIKE ? ESCAPE '\\\\' OR m.content LIKE ? ESCAPE '\\\\') " +
          "ORDER BY c.updated_at DESC LIMIT ?",
          userId, pattern, pattern, MAX_CONVERSATIONS);
      } catch (Exception e) {
        rows = jdbc.queryForList(
          "SELECT DISTINCT c.id, c.title, c.role, c.created_at, c.updated_at " +
          "FROM chat_conversation c " +
          "LEFT JOIN chat_message m ON m.conversation_id = c.id " +
          "WHERE c.user_id = ? AND (c.title LIKE ? ESCAPE '\\\\' OR m.content LIKE ? ESCAPE '\\\\') " +
          "ORDER BY c.updated_at DESC LIMIT ?",
          userId, pattern, pattern, MAX_CONVERSATIONS);
      }

      List<Map<String, Object>> conversations = new ArrayList<>();
      for (Map<String, Object> row : rows) {
        Map<String, Object> conv = new LinkedHashMap<>();
        conv.put("id", str(row.get("id")));
        conv.put("title", str(row.get("title")));
        conv.put("role", str(row.get("role")));
        conv.put("sessionType", str(row.getOrDefault("session_type", "chat")));
        conv.put("createdAt", str(row.get("created_at")));
        conv.put("updatedAt", str(row.get("updated_at")));

        // Include matching message snippets for context
        List<Map<String, Object>> matches = jdbc.queryForList(
          "SELECT content FROM chat_message WHERE conversation_id = ? AND content LIKE ? LIMIT 3",
          str(row.get("id")), pattern);
        List<String> snippets = new ArrayList<>();
        for (Map<String, Object> m : matches) {
          String content = str(m.get("content"));
          // Extract snippet around keyword
          int idx = content.toLowerCase().indexOf(keyword.toLowerCase());
          if (idx >= 0) {
            int start = Math.max(0, idx - 30);
            int end = Math.min(content.length(), idx + keyword.length() + 30);
            String snippet = (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
            snippets.add(snippet);
          }
        }
        conv.put("snippets", snippets);
        conversations.add(conv);
      }
      return ResponseEntity.ok(mapOf("conversations", conversations, "keyword", keyword));
    } catch (Exception e) {
      return ResponseEntity.ok(mapOf("conversations", List.of(), "keyword", keyword));
    }
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

  /** MySQL DATETIME(3) format: yyyy-MM-dd HH:mm:ss.SSS */
  private String now() {
    return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
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

  /**
   * 智能标题生成 — 从用户首条消息中提取关键主题作为会话标题
   * 策略：提取「」《》引号内容、问号前的主题、或截取核心语义片段
   */
  private String generateSmartTitle(String content) {
    if (content == null || content.isBlank()) return "新对话";

    String text = content.trim();

    // Try extracting quoted content first: 「...」 or 《...》
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("[「《]([^」》]+)[」》]").matcher(text);
    if (m.find()) {
      String quoted = m.group(1);
      if (quoted.length() <= 30) return quoted;
      return quoted.substring(0, 27) + "...";
    }

    // If it's a question, extract the core part before ？ or ?
    if (text.contains("？") || text.contains("?")) {
      String questionPart = text.split("[？?]", 2)[0];
      // Remove common question prefixes
      questionPart = questionPart.replaceAll("^(请|帮我|请问|帮我看看|我想了解|怎么|如何|什么是|什么叫做)", "").trim();
      if (!questionPart.isBlank() && questionPart.length() <= 30) return questionPart + "？";
      if (!questionPart.isBlank()) return questionPart.substring(0, 27) + "...？";
    }

    // For commands/requests, extract the key action
    text = text.replaceAll("^(帮我|请|我想|给我|能不能|可以)", "").trim();
    if (text.length() <= 30) return text;
    return text.substring(0, 27) + "...";
  }
}
