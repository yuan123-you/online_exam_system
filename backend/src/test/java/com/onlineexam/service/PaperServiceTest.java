package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class PaperServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private EntityCrudService entityCrudService;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private PaperService paperService;

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
        user.put("name", "User " + id);
        return user;
    }

    private Map<String, Object> makeQuestion(String id, String teacherId, String type, String subject, int score) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("teacherId", teacherId);
        q.put("type", type);
        q.put("subject", subject);
        q.put("score", score);
        q.put("title", "Question " + id);
        q.put("knowledgePoint", "");
        q.put("difficulty", "");
        return q;
    }

    // ================================================================
    // AutoGeneratePaper tests
    // ================================================================

    @Nested
    class AutoGeneratePaperTests {

        @Test
        void teacherCanAutoGeneratePaper() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "single", "Math", 10));
            store.questions.add(makeQuestion("q2", "t1", "single", "Math", 20));

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 2);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Test Paper");
            body.put("durationMinutes", 90);
            body.put("passScore", 60);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            assertNotNull(responseBody);
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) responseBody.get("record");
            assertNotNull(record);
            assertEquals("t1", record.get("teacherId"));
            assertEquals("Test Paper", record.get("name"));
            assertEquals(90, record.get("durationMinutes"));
            assertEquals(60, record.get("passScore"));
            assertEquals("auto", record.get("paperType"));
            assertNotNull(record.get("id"));

            verify(storeService).saveRecord(eq("papers"), any());
            verify(systemLogService).log(any(), eq("auto generate paper"), any());
        }

        @Test
        void nonTeacherGetsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student"));

            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Test Paper");
            body.put("rules", List.of());

            ResponseEntity<?> response = paperService.autoGeneratePaper("s1", body);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            assertEquals("Forbidden.", responseBody.get("message"));

            verify(storeService, never()).saveRecord(any(), any());
        }

        @Test
        void selectsQuestionsMatchingRuleType() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "single", "Math", 10));
            store.questions.add(makeQuestion("q2", "t1", "multiple", "Math", 20));
            store.questions.add(makeQuestion("q3", "t1", "single", "Math", 15));

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 10);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Type Filter Paper");
            body.put("durationMinutes", 60);
            body.put("passScore", 30);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) responseBody.get("record");
            @SuppressWarnings("unchecked")
            List<String> questionIds = (List<String>) record.get("questionIds");

            assertTrue(questionIds.contains("q1"));
            assertTrue(questionIds.contains("q3"));
            assertFalse(questionIds.contains("q2"));
            assertEquals(2, questionIds.size());
        }

        @Test
        void excludesAlreadyOccupiedQuestions() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "single", "Math", 10));
            store.questions.add(makeQuestion("q2", "t1", "single", "Math", 20));

            Map<String, Object> existingPaper = new LinkedHashMap<>();
            existingPaper.put("id", "p0");
            existingPaper.put("questionIds", List.of("q1"));
            store.papers.add(existingPaper);

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 10);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Occupied Paper");
            body.put("durationMinutes", 60);
            body.put("passScore", 10);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) responseBody.get("record");
            @SuppressWarnings("unchecked")
            List<String> questionIds = (List<String>) record.get("questionIds");

            assertFalse(questionIds.contains("q1"));
            assertTrue(questionIds.contains("q2"));
            assertEquals(1, questionIds.size());
        }

        @Test
        void appliesSubjectKpDifficultyFilters() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));

            Map<String, Object> q1 = makeQuestion("q1", "t1", "single", "Math", 10);
            q1.put("knowledgePoint", "Algebra");
            q1.put("difficulty", "easy");

            Map<String, Object> q2 = makeQuestion("q2", "t1", "single", "Physics", 20);
            q2.put("knowledgePoint", "Mechanics");
            q2.put("difficulty", "hard");

            Map<String, Object> q3 = makeQuestion("q3", "t1", "single", "Math", 15);
            q3.put("knowledgePoint", "Algebra");
            q3.put("difficulty", "hard");

            store.questions.addAll(List.of(q1, q2, q3));

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 10);
            rule.put("subject", "Math");
            rule.put("knowledgePoint", "Algebra");
            rule.put("difficulty", "easy");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Filtered Paper");
            body.put("durationMinutes", 60);
            body.put("passScore", 5);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) responseBody.get("record");
            @SuppressWarnings("unchecked")
            List<String> questionIds = (List<String>) record.get("questionIds");

            assertEquals(List.of("q1"), questionIds);
        }

        @Test
        void calculatesTotalScoreCorrectly() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "single", "Math", 10));
            store.questions.add(makeQuestion("q2", "t1", "single", "Math", 25));
            store.questions.add(makeQuestion("q3", "t1", "single", "Math", 15));

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 3);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Score Paper");
            body.put("durationMinutes", 60);
            body.put("passScore", 30);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) responseBody.get("record");

            assertEquals(50, record.get("totalScore"));
        }

        @Test
        void defaultsDurationMinutesTo60WhenZero() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "single", "Math", 10));

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 1);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Default Duration Paper");
            body.put("durationMinutes", 0);
            body.put("passScore", 5);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) responseBody.get("record");

            assertEquals(60, record.get("durationMinutes"));
        }

        @Test
        void validationFailureReturnsBadRequest() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "single", "Math", 10));

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("Paper needs questions.");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 1);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Invalid Paper");
            body.put("durationMinutes", 60);
            body.put("passScore", 5);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            assertEquals("Paper needs questions.", responseBody.get("message"));

            verify(storeService, never()).saveRecord(any(), any());
        }

        @Test
        void selectsUpToCountEvenIfPoolIsSmaller() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher"));
            store.questions.add(makeQuestion("q1", "t1", "single", "Math", 10));

            when(storeService.readStore()).thenReturn(store);
            when(entityCrudService.validate(any(), eq("papers"), any(), isNull())).thenReturn("");

            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", "single");
            rule.put("count", 5);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "Limited Pool Paper");
            body.put("durationMinutes", 60);
            body.put("passScore", 5);
            body.put("rules", List.of(rule));

            ResponseEntity<?> response = paperService.autoGeneratePaper("t1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> record = (Map<String, Object>) responseBody.get("record");
            @SuppressWarnings("unchecked")
            List<String> questionIds = (List<String>) record.get("questionIds");

            assertEquals(1, questionIds.size());
            assertTrue(questionIds.contains("q1"));
            assertEquals(10, record.get("totalScore"));
        }
    }
}
