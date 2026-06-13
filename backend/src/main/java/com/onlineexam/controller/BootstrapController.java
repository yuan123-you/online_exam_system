package com.onlineexam.controller;

import com.onlineexam.service.BootstrapService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bootstrap 控制器 - 处理前端初始化和统计
 */
@RestController
@RequestMapping("/api")
public class BootstrapController {

  private final BootstrapService bootstrapService;

  public BootstrapController(BootstrapService bootstrapService) {
    this.bootstrapService = bootstrapService;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return mapOf("status", "ok", "time", Instant.now().toString(), "storage", "mysql");
  }

  @GetMapping("/bootstrap")
  public ResponseEntity<?> bootstrap(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return bootstrapService.bootstrap(userId);
  }

  @GetMapping("/stats")
  public ResponseEntity<?> stats(@RequestHeader(value = "X-User-Id", required = false) String userId) {
    return bootstrapService.stats(userId);
  }

  private Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
    return map;
  }
}
