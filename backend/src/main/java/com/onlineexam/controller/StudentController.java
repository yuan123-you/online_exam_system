package com.onlineexam.controller;

import com.onlineexam.service.AnalysisService;
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

  private final AnalysisService analysisService;

  public StudentController(AnalysisService analysisService) {
    this.analysisService = analysisService;
  }

  @GetMapping("/student/score-trend")
  public ResponseEntity<?> scoreTrend(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return analysisService.scoreTrend(userId);
  }

  @GetMapping("/student/knowledge-radar")
  public ResponseEntity<?> knowledgeRadar(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return analysisService.knowledgeRadar(userId);
  }
}
