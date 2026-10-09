package com.onlineexam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceImportTest {
  @Mock private StoreService storeService;
  @Mock private SystemLogService systemLogService;
  private AiService service;
  private Store store;

  @org.junit.jupiter.api.AfterEach void noImplicitUpdateDuringCreation() {
    verify(storeService, never()).saveRecord(anyString(), anyMap());
  }

  @BeforeEach
  void setUp() {
    service = new AiService(storeService, systemLogService, new RestTemplate(),
        new ObjectMapper(), Runnable::run, mock(AiCircuitBreaker.class));
    store = new Store();
    store.users.add(Map.of("id", "teacher-1", "role", "teacher"));
    when(storeService.readStore()).thenReturn(store);
  }

  private Map<String, Object> question(String id) {
    return Map.of("id", id, "title", "What is 1 + 1?", "type", "single",
        "options", List.of("1", "2"), "answer", List.of("2"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"teacher-2", "teacher-1"})
  void importedQuestionCannotOverwriteAnExistingQuestion(String owner) {
    store.questions.add(Map.of("id", "existing-id", "teacherId", owner));
    var response = service.importQuestions("teacher-1", Map.of("questions", List.of(question("existing-id"))));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    var record = savedQuestion();
    assertNotEquals("existing-id", record.get("id"));
    assertEquals("teacher-1", record.get("teacherId"));
    assertEquals("What is 1 + 1?", record.get("title"));
  }

  @Test
  void requestIdIsIgnoredEvenWhenAnExistingRecordIsNotInTheStoreSnapshot() {
    // Soft-deleted questions are excluded from readStore but still retain database keys.
    service.importQuestions("teacher-1", Map.of("questions", List.of(question("soft-deleted-id"))));
    assertNotEquals("soft-deleted-id", savedQuestion().get("id"));
  }

  @Test
  void repeatedRequestIdsCreateDistinctQuestions() {
    var response = service.importQuestions("teacher-1", Map.of("questions",
        List.of(question("duplicate-id"), question("duplicate-id"))));
    var captor = recordCaptor();
    verify(storeService, times(2)).createRecord(eq("questions"), captor.capture());
    var records = captor.getAllValues();
    assertNotEquals(records.get(0).get("id"), records.get(1).get("id"));
    assertEquals(2, ((Map<?, ?>) response.getBody()).get("importedCount"));
  }

  @Test
  void missingRequestIdStillCreatesAQuestion() {
    service.importQuestions("teacher-1", Map.of("questions", List.of(question(""))));
    assertFalse(String.valueOf(savedQuestion().get("id")).isBlank());
  }

  @Test
  void studentCannotImportQuestions() {
    store.users = new ArrayList<>(List.of(Map.of("id", "student-1", "role", "student")));
    var response = service.importQuestions("student-1", Map.of("questions", List.of(question("existing-id"))));
    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    verify(storeService, never()).createRecord(anyString(), anyMap());
  }

  private Map<String, Object> savedQuestion() {
    var captor = recordCaptor();
    verify(storeService).createRecord(eq("questions"), captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private ArgumentCaptor<Map<String, Object>> recordCaptor() {
    return ArgumentCaptor.forClass((Class) Map.class);
  }
}
