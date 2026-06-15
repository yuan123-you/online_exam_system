package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.repository.QuestionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private EntityCrudService entityCrudService;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private QuestionService questionService;

    // ================================================================
    // Helper methods
    // ================================================================

    private Store createEmptyStore() {
        Store store = new Store();
        store.departments = new ArrayList<>();
        store.classes = new ArrayList<>();
        store.users = new ArrayList<>();
        store.questions = new ArrayList<>();
        store.papers = new ArrayList<>();
        store.exams = new ArrayList<>();
        store.submissions = new ArrayList<>();
        store.wrongBookEntries = new ArrayList<>();
        store.logs = new ArrayList<>();
        store.backups = new ArrayList<>();
        store.notifications = new ArrayList<>();
        return store;
    }

    private Map<String, Object> makeUser(String id, String role) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("username", id);
        user.put("name", id);
        return user;
    }

    private Map<String, Object> makeQuestion(String id, String teacherId, String title, String subject, String type, int score) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("teacherId", teacherId);
        q.put("title", title);
        q.put("subject", subject);
        q.put("type", type);
        q.put("score", score);
        return q;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseBody(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    // ================================================================
    // ImportQuestions tests
    // ================================================================

    @Nested
    class ImportQuestionsTests {

        @Test
        void teacherCanImportValidQuestions() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("questions"), any(), isNull())).thenReturn("");

            Map<String, Object> q1 = new LinkedHashMap<>(makeQuestion(null, null, "Q1", "Math", "single", 10));
            Map<String, Object> q2 = new LinkedHashMap<>(makeQuestion(null, null, "Q2", "English", "multiple", 20));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("records", List.of(q1, q2));

            ResponseEntity<?> response = questionService.importQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(2, result.get("importedCount"));
            assertEquals(0, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> created = (List<Map<String, Object>>) result.get("created");
            assertEquals(2, created.size());

            verify(storeService, times(2)).saveRecord(eq("questions"), any());
            verify(systemLogService).log(any(), eq("batch import questions"), eq("2"));
        }

        @Test
        void nonTeacherGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("records", List.of());

            ResponseEntity<?> response = questionService.importQuestions("s1", body);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals("Forbidden.", result.get("message"));
        }

        @Test
        void invalidQuestionsGoToErrors() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("questions"), any(), isNull()))
                    .thenReturn("Question fields are incomplete.");

            Map<String, Object> badQ = new LinkedHashMap<>(makeQuestion(null, null, "", "", "single", 10));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("records", List.of(badQ));

            ResponseEntity<?> response = questionService.importQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(0, result.get("importedCount"));
            assertEquals(1, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            assertEquals(1, errors.size());
            assertEquals(0, errors.get(0).get("index"));
            assertEquals("Question fields are incomplete.", errors.get(0).get("message"));

            verify(storeService, never()).saveRecord(any(), any());
        }

        @Test
        void enforces5000Limit() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            // Fill 5000 existing questions for this teacher
            for (int i = 0; i < 5000; i++) {
                store.questions.add(makeQuestion("q" + i, "t1", "Q" + i, "Math", "single", 10));
            }
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> newQ = new LinkedHashMap<>(makeQuestion(null, null, "Overflow", "Math", "single", 10));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("records", List.of(newQ));

            ResponseEntity<?> response = questionService.importQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(0, result.get("importedCount"));
            assertEquals(1, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            assertTrue(String.valueOf(errors.get(0).get("message")).contains("5000"));
        }

        @Test
        void setsTeacherIdOnEachRecord() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("questions"), any(), isNull())).thenReturn("");

            Map<String, Object> q1 = new LinkedHashMap<>(makeQuestion(null, "otherTeacher", "Q1", "Math", "single", 10));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("records", List.of(q1));

            ResponseEntity<?> response = questionService.importQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            // Verify that saveRecord was called with a record that has teacherId = "t1"
            verify(storeService).saveRecord(eq("questions"), argThat(record ->
                    "t1".equals(String.valueOf(record.get("teacherId")))
            ));
        }
    }

    // ================================================================
    // DeleteQuestions tests
    // ================================================================

    @Nested
    class DeleteQuestionsTests {

        @Test
        void teacherCanDeleteOwnQuestions() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "Q1", "Math", "single", 10));
            store.questions.add(makeQuestion("q2", "t1", "Q2", "Math", "single", 10));
            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.deleteBlocker(any(), eq("questions"), anyString())).thenReturn("");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1", "q2"));

            ResponseEntity<?> response = questionService.deleteQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(2, result.get("deletedCount"));
            assertEquals(0, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<String> deletedIds = (List<String>) result.get("deletedIds");
            assertTrue(deletedIds.contains("q1"));
            assertTrue(deletedIds.contains("q2"));

            verify(storeService, times(2)).deleteRecord(eq("questions"), anyString());
            verify(systemLogService).log(any(), eq("batch delete questions"), eq("2"));
        }

        @Test
        void nonTeacherGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1"));

            ResponseEntity<?> response = questionService.deleteQuestions("s1", body);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void cannotDeleteOtherTeachersQuestions() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t2", "Q1", "Math", "single", 10));

            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1"));

            ResponseEntity<?> response = questionService.deleteQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(0, result.get("deletedCount"));
            assertEquals(1, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            assertEquals("q1", errors.get(0).get("id"));
            assertEquals("Question not found.", errors.get(0).get("message"));

            verify(storeService, never()).deleteRecord(any(), anyString());
        }

        @Test
        void blockedQuestionsGoToErrors() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "Q1", "Math", "single", 10));
            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.deleteBlocker(any(), eq("questions"), eq("q1")))
                    .thenReturn("该题目已被试卷引用，无法删除。");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1"));

            ResponseEntity<?> response = questionService.deleteQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(0, result.get("deletedCount"));
            assertEquals(1, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            assertEquals("q1", errors.get(0).get("id"));
            assertEquals("该题目已被试卷引用，无法删除。", errors.get(0).get("message"));

            verify(storeService, never()).deleteRecord(any(), anyString());
        }

        @Test
        void notFoundQuestionsGoToErrors() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("nonexistent"));

            ResponseEntity<?> response = questionService.deleteQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(0, result.get("deletedCount"));
            assertEquals(1, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            assertEquals("nonexistent", errors.get(0).get("id"));
            assertEquals("Question not found.", errors.get(0).get("message"));
        }
    }

    // ================================================================
    // RestoreQuestions tests
    // ================================================================

    @Nested
    class RestoreQuestionsTests {

        @Test
        void teacherCanRestoreOwnSoftDeletedQuestions() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            // The question is NOT in active store.questions (it's soft-deleted)
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.findTeacherIdIncludingDeleted("q1")).thenReturn("t1");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1"));

            ResponseEntity<?> response = questionService.restoreQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(1, result.get("restoredCount"));
            assertEquals(0, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<String> restoredIds = (List<String>) result.get("restoredIds");
            assertTrue(restoredIds.contains("q1"));

            verify(storeService).restoreRecord("questions", "q1");
            verify(systemLogService).log(any(), eq("batch restore questions"), eq("1"));
        }

        @Test
        void nonTeacherGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student"));
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1"));

            ResponseEntity<?> response = questionService.restoreQuestions("s1", body);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void checksOwnershipViaQuestionRepositoryForSoftDeleted() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            // Question not in active store, but exists in DB owned by different teacher
            when(storeService.readStore()).thenReturn(store);
            when(questionRepository.findTeacherIdIncludingDeleted("q1")).thenReturn("t2");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1"));

            ResponseEntity<?> response = questionService.restoreQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(0, result.get("restoredCount"));
            assertEquals(1, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            assertEquals("q1", errors.get(0).get("id"));
            assertEquals("Question not owned by you.", errors.get(0).get("message"));

            verify(storeService, never()).restoreRecord(any(), anyString());
        }

        @Test
        void notFoundQuestionsGoToErrors() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            when(storeService.readStore()).thenReturn(store);
            // Not in active store, and not in DB either
            when(questionRepository.findTeacherIdIncludingDeleted("q1")).thenReturn(null);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of("q1"));

            ResponseEntity<?> response = questionService.restoreQuestions("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            Map<String, Object> result = responseBody(response);
            assertEquals(0, result.get("restoredCount"));
            assertEquals(1, result.get("failedCount"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("errors");
            assertEquals("q1", errors.get(0).get("id"));
            assertEquals("Question not found.", errors.get(0).get("message"));

            verify(storeService, never()).restoreRecord(any(), anyString());
        }
    }
}
