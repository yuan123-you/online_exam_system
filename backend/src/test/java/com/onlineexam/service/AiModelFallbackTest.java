package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiModelFallbackTest {
  @ParameterizedTest @ValueSource(strings = {"general", "intent"})
  void synchronousRetryActuallySendsFallbackModel(String operation) {
    RestTemplate rest = mock(RestTemplate.class);
    AiService service = new AiService(mock(StoreService.class), mock(SystemLogService.class), rest,
        new ObjectMapper(), Runnable::run, mock(AiCircuitBreaker.class));
    ReflectionTestUtils.setField(service, "apiKey", "test-not-real");
    ReflectionTestUtils.setField(service, "apiUrl", "http://provider.invalid/chat");
    ReflectionTestUtils.setField(service, "model", "glm-4.7-flash");
    ReflectionTestUtils.setField(service, "concurrentLimit", 1);
    ReflectionTestUtils.setField(service, "fallbackModel", "glm-4-flash");
    List<String> models = new ArrayList<>();
    when(rest.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class))).thenAnswer(invocation -> {
      HttpEntity<Map<String, Object>> entity = invocation.getArgument(2);
      models.add(String.valueOf(entity.getBody().get("model")));
      if (models.size() == 1) throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
      return ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", "ok")))));
    });
    if (operation.equals("general")) assertEquals("ok", service.callAiApi("system", "user"));
    else assertEquals("ok", ReflectionTestUtils.invokeMethod(service, "callAiApiForIntentAnalysis", "user"));
    assertEquals(List.of("glm-4.7-flash", "glm-4-flash"), models);
  }
}
