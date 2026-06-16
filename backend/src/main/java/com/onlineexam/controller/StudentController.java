package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.AnalysisService;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生控制器 - 处理成绩趋势和知识点雷达
 */
@RestController
@RequestMapping("/api")
public class StudentController {

  private final StoreService storeService;
  private final AnalysisService analysisService;

  public StudentController(StoreService storeService, AnalysisService analysisService) {
    this.storeService = storeService;
    this.analysisService = analysisService;
  }

  @GetMapping("/student/score-trend")
  public ResponseEntity<?> scoreTrend(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    return analysisService.scoreTrend(userId);
  }

  @GetMapping("/student/knowledge-radar")
  public ResponseEntity<?> knowledgeRadar(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    Store store = storeService.readStore();
    Map<String, Object> user = find(store.users, userId);
    if (!isRole(user, "student")) return error(HttpStatus.FORBIDDEN, "Forbidden.");
    return analysisService.knowledgeRadar(userId);
  }

  private Map<String, Object> find(java.util.List<Map<String, Object>> rows, String id) {
    if (id == null) return null;
    return rows.stream().filter(row -> Objects.equals(str(row, "id"), id)).findFirst().orElse(null);
  }

  private boolean isRole(Map<String, Object> user, String role) {
    return user != null && Objects.equals(str(user, "role"), role);
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(mapOf("message", message));
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
