package com.onlineexam.controller;

import com.onlineexam.service.ChatHistoryService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天历史控制器 — 会话和消息的持久化管理
 */
@RestController
@RequestMapping("/api/chat")
public class ChatHistoryController {

  private final ChatHistoryService chatHistoryService;

  public ChatHistoryController(ChatHistoryService chatHistoryService) {
    this.chatHistoryService = chatHistoryService;
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
}
