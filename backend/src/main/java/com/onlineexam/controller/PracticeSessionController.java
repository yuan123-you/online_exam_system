package com.onlineexam.controller;

import com.onlineexam.service.PracticeSessionService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 练习会话控制器 - 处理练习会话的创建、查询、答题保存和提交
 */
@RestController
@RequestMapping("/api/practice")
public class PracticeSessionController {

  private final PracticeSessionService practiceSessionService;

  public PracticeSessionController(PracticeSessionService practiceSessionService) {
    this.practiceSessionService = practiceSessionService;
  }

  /** 创建新的练习会话 */
  @PostMapping("/sessions")
  public ResponseEntity<?> createSession(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @RequestBody Map<String, Object> body) {
    return practiceSessionService.createSession(userId, body);
  }

  /** 获取用户的练习会话列表 */
  @GetMapping("/sessions")
  public ResponseEntity<?> listSessions(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return practiceSessionService.listSessions(userId);
  }

  /** 获取指定练习会话的完整状态 */
  @GetMapping("/sessions/{sessionId}")
  public ResponseEntity<?> getSession(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @PathVariable String sessionId) {
    return practiceSessionService.getSession(userId, sessionId);
  }

  /** 获取用户当前活跃的练习会话（用于页面刷新恢复） */
  @GetMapping("/sessions/active")
  public ResponseEntity<?> getActiveSession(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return practiceSessionService.getActiveSession(userId);
  }

  /** 保存/更新练习会话的题目列表 */
  @PutMapping("/sessions/{sessionId}/questions")
  public ResponseEntity<?> saveQuestions(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @PathVariable String sessionId,
                                          @RequestBody Map<String, Object> body) {
    return practiceSessionService.saveQuestions(userId, sessionId, body);
  }

  /** 批量保存用户答案 */
  @PutMapping("/sessions/{sessionId}/answers")
  public ResponseEntity<?> saveAnswers(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                        @PathVariable String sessionId,
                                        @RequestBody Map<String, Object> body) {
    return practiceSessionService.saveAnswers(userId, sessionId, body);
  }

  /** 保存单道题的用户答案 */
  @PutMapping("/questions/{questionId}/answer")
  public ResponseEntity<?> saveAnswer(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @PathVariable String questionId,
                                       @RequestBody Map<String, Object> body) {
    return practiceSessionService.saveAnswer(userId, questionId, body);
  }

  /** 提交整个练习会话的所有答案 */
  @PostMapping("/sessions/{sessionId}/submit")
  public ResponseEntity<?> submitSession(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @PathVariable String sessionId,
                                          @RequestBody Map<String, Object> body) {
    return practiceSessionService.submitSession(userId, sessionId, body);
  }

  /** 删除练习会话 */
  @DeleteMapping("/sessions/{sessionId}")
  public ResponseEntity<?> deleteSession(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @PathVariable String sessionId) {
    return practiceSessionService.deleteSession(userId, sessionId);
  }
}
