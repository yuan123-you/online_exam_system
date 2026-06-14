package com.onlineexam.controller;

import com.onlineexam.service.ChatHistoryService;
import com.onlineexam.service.UserPreferenceService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天历史控制器 — 会话和消息的持久化管理（含搜索与智能标题）
 */
@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

  private final ChatHistoryService chatHistoryService;
  private final UserPreferenceService userPreferenceService;

  public ChatHistoryController(ChatHistoryService chatHistoryService,
                                UserPreferenceService userPreferenceService) {
    this.chatHistoryService = chatHistoryService;
    this.userPreferenceService = userPreferenceService;
  }

  /** 获取用户的会话列表 */
  @GetMapping("/conversations")
  public ResponseEntity<?> listConversations(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return chatHistoryService.listConversations(userId);
  }

  /** 获取指定会话的所有消息 */
  @GetMapping("/conversations/{conversationId}")
  public ResponseEntity<?> getMessages(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @PathVariable String conversationId) {
    return chatHistoryService.getMessages(userId, conversationId);
  }

  /** 新建会话 */
  @PostMapping("/conversations")
  public ResponseEntity<?> createConversation(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @RequestBody Map<String, Object> body) {
    return chatHistoryService.createConversation(userId, body);
  }

  /** 向会话追加消息 */
  @PostMapping("/conversations/{conversationId}/messages")
  public ResponseEntity<?> appendMessages(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @PathVariable String conversationId,
                                          @RequestBody Map<String, Object> body) {
    return chatHistoryService.appendMessages(userId, conversationId, body);
  }

  /** 删除会话 */
  @DeleteMapping("/conversations/{conversationId}")
  public ResponseEntity<?> deleteConversation(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                              @PathVariable String conversationId) {
    return chatHistoryService.deleteConversation(userId, conversationId);
  }

  /** 搜索历史对话（按关键词匹配标题和消息内容） */
  @GetMapping("/conversations/search")
  public ResponseEntity<?> searchConversations(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                @RequestParam String keyword) {
    return chatHistoryService.searchConversations(userId, keyword);
  }

  /** 获取用户学习偏好分析（根据历史对话推理） */
  @GetMapping("/preferences")
  public ResponseEntity<?> getPreferences(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return userPreferenceService.analyzePreferences(userId);
  }
}
