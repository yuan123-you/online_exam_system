package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WrongBookServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private WrongBookService wrongBookService;

    // ================================================================
    // Helper methods
    // ================================================================

    private Store createEmptyStore() {
        Store store = new Store();
        store.wrongBookEntries = new ArrayList<>();
        store.questions = new ArrayList<>();
        store.users = new ArrayList<>();
        store.submissions = new ArrayList<>();
        return store;
    }

    private Map<String, Object> makeActiveEntry(String id, String studentId, String questionId,
                                                  String lastWrongAt, int wrongCount) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("studentId", studentId);
        entry.put("questionId", questionId);
        entry.put("status", "active");
        entry.put("lastWrongAt", lastWrongAt);
        entry.put("wrongCount", wrongCount);
        entry.put("fullScore", 10);
        entry.put("lastScore", 3);
        entry.put("studentName", "TestStudent");
        entry.put("subject", "Math");
        entry.put("knowledgePoint", "Algebra");
        entry.put("type", "single");
        entry.put("title", "Test Question");
        entry.put("latestAnswer", List.of("A"));
        entry.put("expectedAnswer", List.of("B"));
        entry.put("lastRetryCorrect", false);
        entry.put("removable", false);
        return entry;
    }

    private Map<String, Object> makeArchivedEntry(String id, String studentId, String questionId) {
        Map<String, Object> entry = makeActiveEntry(id, studentId, questionId,
                Instant.now().minus(10, ChronoUnit.DAYS).toString(), 1);
        entry.put("status", "archived");
        return entry;
    }

    private Map<String, Object> makePracticeEntry(String id, String studentId, String questionId) {
        Map<String, Object> entry = makeActiveEntry(id, studentId, questionId,
                Instant.now().minus(5, ChronoUnit.DAYS).toString(), 1);
        entry.put("status", "practice");
        return entry;
    }

    private Map<String, Object> makeRemovedEntry(String id, String studentId, String questionId) {
        Map<String, Object> entry = makeActiveEntry(id, studentId, questionId,
                Instant.now().minus(3, ChronoUnit.DAYS).toString(), 1);
        entry.put("removedAt", Instant.now().toString());
        return entry;
    }

    private Map<String, Object> makeQuestion(String id, String subject, String knowledgePoint,
                                               String type, String title, List<Object> answer) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("subject", subject);
        q.put("knowledgePoint", knowledgePoint);
        q.put("type", type);
        q.put("title", title);
        q.put("answer", answer);
        q.put("score", 10);
        return q;
    }

    private Map<String, Object> makeSubmission(String studentId, String studentName,
                                                 String examId, String submittedAt,
                                                 List<Object> answerDetail) {
        Map<String, Object> submission = new LinkedHashMap<>();
        submission.put("id", "sub-1");
        submission.put("studentId", studentId);
        submission.put("studentName", studentName);
        submission.put("examId", examId);
        submission.put("submittedAt", submittedAt);
        submission.put("answerDetail", answerDetail);
        return submission;
    }

    private Map<String, Object> makeAnswerDetail(String questionId, int score, int fullScore,
                                                   List<Object> answer) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("questionId", questionId);
        detail.put("score", score);
        detail.put("fullScore", fullScore);
        detail.put("answer", answer);
        detail.put("subject", "Math");
        detail.put("knowledgePoint", "Algebra");
        detail.put("type", "single");
        detail.put("title", "Test Question");
        detail.put("expectedAnswer", List.of("B"));
        return detail;
    }

    // ================================================================
    // AutoArchive Tests
    // ================================================================

    @Nested
    class AutoArchiveTests {

        @Test
        void belowLimit_doesNothing() {
            Store store = createEmptyStore();
            // Add 999 active entries (below 1000 limit)
            for (int i = 0; i < 999; i++) {
                store.wrongBookEntries.add(
                        makeActiveEntry("w" + i, "s1", "q" + i,
                                Instant.now().minus(i, ChronoUnit.MINUTES).toString(), 1));
            }

            wrongBookService.autoArchiveIfNeeded(store, "s1");

            // No entries should be archived
            long archivedCount = store.wrongBookEntries.stream()
                    .filter(e -> "archived".equals(String.valueOf(e.get("status"))))
                    .count();
            assertEquals(0, archivedCount);
            verify(storeService, never()).saveRecord(anyString(), anyMap());
            verify(systemLogService, never()).log(any(), anyString(), anyString());
        }

        @Test
        void atLimit_archivesOldest50() {
            Store store = createEmptyStore();
            // Add 1000 active entries with different lastWrongAt timestamps
            for (int i = 0; i < 1000; i++) {
                store.wrongBookEntries.add(
                        makeActiveEntry("w" + i, "s1", "q" + i,
                                Instant.now().minus(1000 - i, ChronoUnit.MINUTES).toString(), 1));
            }

            wrongBookService.autoArchiveIfNeeded(store, "s1");

            // 50 entries should be archived
            long archivedCount = store.wrongBookEntries.stream()
                    .filter(e -> "archived".equals(String.valueOf(e.get("status"))))
                    .count();
            assertEquals(50, archivedCount);

            // The oldest 50 entries (w0..w49) should be archived
            for (int i = 0; i < 50; i++) {
                Map<String, Object> entry = store.wrongBookEntries.get(i);
                assertEquals("archived", String.valueOf(entry.get("status")));
                assertNotNull(entry.get("archivedAt"));
            }

            // Remaining entries should still be active
            for (int i = 50; i < 1000; i++) {
                Map<String, Object> entry = store.wrongBookEntries.get(i);
                assertEquals("active", String.valueOf(entry.get("status")));
            }

            verify(storeService, times(50)).saveRecord(eq("wrongBookEntries"), anyMap());
            verify(systemLogService, times(1)).log(isNull(), eq("auto-archive wrong book"), contains("archived=50"));
        }

        @Test
        void onlyActiveEntriesCounted_excludesArchivedPracticeAndRemoved() {
            Store store = createEmptyStore();
            // 800 active entries
            for (int i = 0; i < 800; i++) {
                store.wrongBookEntries.add(
                        makeActiveEntry("active" + i, "s1", "q" + i,
                                Instant.now().minus(800 - i, ChronoUnit.MINUTES).toString(), 1));
            }
            // 100 archived entries (should not count toward limit)
            for (int i = 0; i < 100; i++) {
                store.wrongBookEntries.add(
                        makeArchivedEntry("arch" + i, "s1", "aq" + i));
            }
            // 50 practice entries (should not count toward limit)
            for (int i = 0; i < 50; i++) {
                store.wrongBookEntries.add(
                        makePracticeEntry("prac" + i, "s1", "pq" + i));
            }
            // 50 removed entries (should not count toward limit)
            for (int i = 0; i < 50; i++) {
                store.wrongBookEntries.add(
                        makeRemovedEntry("rem" + i, "s1", "rq" + i));
            }
            // Total = 1000, but only 800 are active, so below limit

            wrongBookService.autoArchiveIfNeeded(store, "s1");

            // No entries should be newly archived
            long newlyArchived = store.wrongBookEntries.stream()
                    .filter(e -> "archived".equals(String.valueOf(e.get("status")))
                            && e.containsKey("archivedAt"))
                    .count();
            // The 100 pre-existing archived entries have no archivedAt set by us
            // So newly archived should be 0
            long withArchivedAt = store.wrongBookEntries.stream()
                    .filter(e -> e.containsKey("archivedAt"))
                    .count();
            assertEquals(0, withArchivedAt);
            verify(storeService, never()).saveRecord(anyString(), anyMap());
        }
    }

    // ================================================================
    // SyncFromSubmission Tests
    // ================================================================

    @Nested
    class SyncFromSubmissionTests {

        @Test
        void correctAnswersNotAddedToWrongBook() {
            Store store = createEmptyStore();
            Map<String, Object> correctDetail = makeAnswerDetail("q1", 10, 10, List.of("B"));
            Map<String, Object> submission = makeSubmission("s1", "Alice", "exam1",
                    Instant.now().toString(), List.of(correctDetail));

            wrongBookService.syncFromSubmission(store, submission);

            assertTrue(store.wrongBookEntries.isEmpty());
            verify(storeService, never()).saveRecord(eq("wrongBookEntries"), anyMap());
        }

        @Test
        void wrongAnswerCreatesNewEntry() {
            Store store = createEmptyStore();
            Map<String, Object> wrongDetail = makeAnswerDetail("q1", 3, 10, List.of("A"));
            Map<String, Object> submission = makeSubmission("s1", "Alice", "exam1",
                    Instant.now().toString(), List.of(wrongDetail));

            wrongBookService.syncFromSubmission(store, submission);

            // The entry is saved via storeService.saveRecord, but not added to the list
            // because the service doesn't add to the list in memory — it relies on saveRecord
            verify(storeService, times(1)).saveRecord(eq("wrongBookEntries"), argThat(entry -> {
                assertEquals("s1", String.valueOf(entry.get("studentId")));
                assertEquals("Alice", String.valueOf(entry.get("studentName")));
                assertEquals("q1", String.valueOf(entry.get("questionId")));
                assertEquals(10, entry.get("fullScore"));
                assertEquals(3, entry.get("lastScore"));
                assertEquals(1, entry.get("wrongCount"));
                assertEquals(false, entry.get("lastRetryCorrect"));
                assertEquals(false, entry.get("removable"));
                assertEquals("active", String.valueOf(entry.get("status")));
                return true;
            }));
        }

        @Test
        void existingWrongEntryGetsWrongCountIncremented() {
            Store store = createEmptyStore();
            // Pre-existing wrong entry with wrongCount=2
            Map<String, Object> existingEntry = makeActiveEntry("w1", "s1", "q1",
                    Instant.now().minus(1, ChronoUnit.DAYS).toString(), 2);
            store.wrongBookEntries.add(existingEntry);

            Map<String, Object> wrongDetail = makeAnswerDetail("q1", 5, 10, List.of("A"));
            Map<String, Object> submission = makeSubmission("s1", "Alice", "exam1",
                    Instant.now().toString(), List.of(wrongDetail));

            wrongBookService.syncFromSubmission(store, submission);

            verify(storeService, times(1)).saveRecord(eq("wrongBookEntries"), argThat(entry -> {
                int wrongCount = entry.get("wrongCount") instanceof Number n
                        ? n.intValue() : Integer.parseInt(String.valueOf(entry.get("wrongCount")));
                assertEquals(3, wrongCount); // 2 + 1
                assertEquals(5, entry.get("lastScore"));
                assertEquals(false, entry.get("lastRetryCorrect"));
                assertEquals("active", String.valueOf(entry.get("status")));
                return true;
            }));
        }

        @Test
        void respects1000Limit() {
            Store store = createEmptyStore();
            // Fill up 1000 active entries
            for (int i = 0; i < 1000; i++) {
                store.wrongBookEntries.add(
                        makeActiveEntry("w" + i, "s1", "q" + i,
                                Instant.now().minus(1000 - i, ChronoUnit.MINUTES).toString(), 1));
            }

            Map<String, Object> wrongDetail = makeAnswerDetail("q_new", 3, 10, List.of("A"));
            Map<String, Object> submission = makeSubmission("s1", "Alice", "exam1",
                    Instant.now().toString(), List.of(wrongDetail));

            wrongBookService.syncFromSubmission(store, submission);

            // autoArchiveIfNeeded should have been called, archiving 50 oldest
            // Then the new entry should be saved since space was freed
            verify(storeService, atLeastOnce()).saveRecord(eq("wrongBookEntries"), anyMap());
        }
    }

    // ================================================================
    // BuildWrongBookEntry Tests
    // ================================================================

    @Nested
    class BuildWrongBookEntryTests {

        @Test
        void lastRetryCorrectTrue_showsAlreadyPassedAndRemovableTrue() {
            Store store = createEmptyStore();
            Map<String, Object> entry = makeActiveEntry("w1", "s1", "q1",
                    Instant.now().toString(), 1);
            entry.put("lastRetryCorrect", true);

            Map<String, Object> result = wrongBookService.buildWrongBookEntry(store, entry);

            assertEquals("已重做通过", result.get("statusText"));
            assertEquals(true, result.get("removable"));
        }

        @Test
        void noRetry_showsPendingAndRemovableFalse() {
            Store store = createEmptyStore();
            Map<String, Object> entry = makeActiveEntry("w1", "s1", "q1",
                    Instant.now().toString(), 1);
            entry.put("lastRetryCorrect", false);

            Map<String, Object> result = wrongBookService.buildWrongBookEntry(store, entry);

            assertEquals("待重做", result.get("statusText"));
            assertEquals(false, result.get("removable"));
        }

        @Test
        void enrichesWithQuestionInfoWhenQuestionExists() {
            Store store = createEmptyStore();
            Map<String, Object> question = makeQuestion("q1", "Physics", "Mechanics",
                    "multi", "Force Problem", List.of("C", "D"));
            store.questions.add(question);

            Map<String, Object> entry = makeActiveEntry("w1", "s1", "q1",
                    Instant.now().toString(), 1);
            entry.put("lastRetryCorrect", false);

            Map<String, Object> result = wrongBookService.buildWrongBookEntry(store, entry);

            // Should be enriched with question info
            assertEquals("Physics", result.get("subject"));
            assertEquals("Mechanics", result.get("knowledgePoint"));
            assertEquals("multi", result.get("type"));
            assertEquals("Force Problem", result.get("title"));
            assertEquals(List.of("C", "D"), result.get("expectedAnswer"));

            // Should include sanitized question object (without answer key)
            @SuppressWarnings("unchecked")
            Map<String, Object> questionObj = (Map<String, Object>) result.get("question");
            assertNotNull(questionObj);
            assertNull(questionObj.get("answer"), "Question object should have answer removed");
            assertEquals("Physics", questionObj.get("subject"));
        }

        @Test
        void lastRetryCorrectTrueWithRemovedAt_removableFalse() {
            Store store = createEmptyStore();
            Map<String, Object> entry = makeActiveEntry("w1", "s1", "q1",
                    Instant.now().toString(), 1);
            entry.put("lastRetryCorrect", true);
            entry.put("removedAt", Instant.now().toString());

            Map<String, Object> result = wrongBookService.buildWrongBookEntry(store, entry);

            assertEquals("已重做通过", result.get("statusText"));
            assertEquals(false, result.get("removable"));
        }
    }
}
