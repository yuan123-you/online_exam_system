package com.onlineexam.controller;

import com.onlineexam.service.UserPreferenceService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个性化推荐控制器 — 提供推荐、画像、行为日志、反馈等API端点
 */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final UserPreferenceService userPreferenceService;

  public RecommendationController(UserPreferenceService userPreferenceService) {
    this.userPreferenceService = userPreferenceService;
  }

  /**
   * 获取个性化推荐列表
   */
  @GetMapping
  public ResponseEntity<?> getRecommendations(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return userPreferenceService.getRecommendations(userId);
  }

  /**
   * 获取用户画像
   */
  @GetMapping("/profile")
  public ResponseEntity<?> getUserProfile(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return userPreferenceService.getUserProfile(userId);
  }

  /**
   * 记录用户行为日志
   */
  @PostMapping("/behavior")
  public ResponseEntity<?> logBehavior(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                       @RequestBody Map<String, Object> body) {
    return userPreferenceService.logBehavior(userId, body);
  }

  /**
   * 提交推荐反馈
   */
  @PostMapping("/feedback")
  public ResponseEntity<?> submitFeedback(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                          @RequestBody Map<String, Object> body) {
    return userPreferenceService.submitFeedback(userId, body);
  }
}
