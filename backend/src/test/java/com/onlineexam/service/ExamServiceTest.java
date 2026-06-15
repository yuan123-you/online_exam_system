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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private ExamService examService;

    // ================================================================
    // Helper methods
    // ================================================================

    private Store createEmptyStore() {
        Store store = new Store();
        store.papers = new ArrayList<>();
        store.questions = new ArrayList<>();
        store.exams = new ArrayList<>();
        store.submissions = new ArrayList<>();
        store.users = new ArrayList<>();
        return store;
    }

    private Map<String, Object> makeExam(String id, String paperId, Instant start, Instant end, boolean published, List<Object> targetClassIds) {
        Map<String, Object> exam = new LinkedHashMap<>();
        exam.put("id", id);
        exam.put("paperId", paperId);
        exam.put("name", "Test Exam");
        exam.put("startTime", start.toString());
        exam.put("endTime", end.toString());
        exam.put("published", published);
        exam.put("targetClassIds", targetClassIds);
        return exam;
    }

    private Map<String, Object> makePaper(String id, int durationMinutes, int totalScore, int passScore, List<Object> questionIds) {
        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("id", id);
        paper.put("name", "Test Paper");
        paper.put("durationMinutes", durationMinutes);
        paper.put("totalScore", totalScore);
        paper.put("passScore", passScore);
        paper.put("questionIds", questionIds);
        return paper;
    }

    private Map<String, Object> makeQuestion(String id, String title, List<Object> options, List<Object> answer, int score) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("title", title);
        q.put("options", options);
        q.put("answer", answer);
        q.put("score", score);
        q.put("type", "single");
        q.put("subject", "Math");
        return q;
    }

    private Map<String, Object> makeUser(String id, String role, String name, String classId) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("name", name);
        user.put("classId", classId);
        return user;
    }

    // ================================================================
    // Exam Status tests
    // ================================================================

    @Nested
    class ExamStatusTests {

        @Test
        void examStatus_currentTimeBetweenStartAndEnd_returnsRunning() {
            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of());

            assertEquals("进行中", examService.examStatus(exam));
        }

        @Test
        void examStatus_startTimeInFuture_returnsUpcoming() {
            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    Instant.now().plus(2, ChronoUnit.HOURS),
                    true, List.of());

            assertEquals("未开始", examService.examStatus(exam));
        }

        @Test
        void examStatus_endTimeInPast_returnsEnded() {
            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(2, ChronoUnit.HOURS),
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    true, List.of());

            assertEquals("已结束", examService.examStatus(exam));
        }
    }

    // ================================================================
    // Decorate Exam tests
    // ================================================================

    @Nested
    class DecorateExamTests {

        @Test
        void decorateExam_withValidPaper_addsPaperInfoAndStatus() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1", "q2"));
            store.papers.add(paper);

            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of());

            Map<String, Object> result = examService.decorateExam(store, exam);

            assertEquals("进行中", result.get("statusText"));
            assertEquals(90, result.get("durationMinutes"));
            assertEquals(100, result.get("totalScore"));
            assertEquals(60, result.get("passScore"));
            assertEquals("Test Paper", result.get("paperName"));
            // Original exam fields should be preserved
            assertEquals("e1", result.get("id"));
        }

        @Test
        void decorateExam_missingPaper_usesDefaults() {
            Store store = createEmptyStore();

            Map<String, Object> exam = makeExam("e1", "nonexistent",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of());

            Map<String, Object> result = examService.decorateExam(store, exam);

            assertEquals(0, result.get("durationMinutes"));
            assertEquals(0, result.get("totalScore"));
            assertEquals(0, result.get("passScore"));
            assertEquals("-", result.get("paperName"));
        }
    }

    // ================================================================
    // Student Access tests
    // ================================================================

    @Nested
    class StudentAccessTests {

        @Test
        void canStudentAccess_publishedExamAndMatchingClass_returnsTrue() {
            Map<String, Object> student = makeUser("s1", "student", "Alice", "c1");
            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of("c1", "c2"));

            assertTrue(examService.canStudentAccess(student, exam));
        }

        @Test
        void canStudentAccess_unpublishedExam_returnsFalse() {
            Map<String, Object> student = makeUser("s1", "student", "Alice", "c1");
            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    false, List.of("c1"));

            assertFalse(examService.canStudentAccess(student, exam));
        }

        @Test
        void canStudentAccess_wrongClass_returnsFalse() {
            Map<String, Object> student = makeUser("s1", "student", "Alice", "c3");
            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of("c1", "c2"));

            assertFalse(examService.canStudentAccess(student, exam));
        }

        @Test
        void canStudentAccess_teacherRole_returnsFalse() {
            Map<String, Object> teacher = makeUser("t1", "teacher", "Bob", "c1");
            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of("c1"));

            assertFalse(examService.canStudentAccess(teacher, exam));
        }
    }

    // ================================================================
    // Time Calculation tests
    // ================================================================

    @Nested
    class TimeCalculationTests {

        @Test
        void computeDeadline_startPlusDurationBeforeEnd_returnsDeadline() {
            Instant start = Instant.parse("2025-06-01T10:00:00Z");
            Instant end = Instant.parse("2025-06-01T14:00:00Z"); // 4 hours window
            Map<String, Object> exam = makeExam("e1", "p1", start, end, true, List.of());
            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of()); // 90 min duration

            String deadline = examService.computeDeadline(exam, paper, start.toString());

            // start + 90min = 11:30, which is before end (14:00)
            Instant expected = start.plusSeconds(90 * 60L);
            assertEquals(expected.toString(), deadline);
        }

        @Test
        void computeDeadline_startPlusDurationAfterEnd_returnsEndTime() {
            Instant start = Instant.parse("2025-06-01T10:00:00Z");
            Instant end = Instant.parse("2025-06-01T11:00:00Z"); // only 1 hour window
            Map<String, Object> exam = makeExam("e1", "p1", start, end, true, List.of());
            Map<String, Object> paper = makePaper("p1", 120, 100, 60, List.of()); // 120 min duration

            String deadline = examService.computeDeadline(exam, paper, start.toString());

            // start + 120min = 12:00, which is after end (11:00), so deadline = end
            assertEquals(end.toString(), deadline);
        }

        @Test
        void remainingMs_futureDeadline_returnsPositive() {
            Map<String, Object> submission = new LinkedHashMap<>();
            submission.put("deadlineAt", Instant.now().plusSeconds(3600).toString());

            long ms = examService.remainingMs(submission);

            assertTrue(ms > 0);
            assertTrue(ms <= 3600_000);
        }

        @Test
        void remainingMs_pastDeadline_returnsZero() {
            Map<String, Object> submission = new LinkedHashMap<>();
            submission.put("deadlineAt", Instant.now().minusSeconds(3600).toString());

            assertEquals(0L, examService.remainingMs(submission));
        }

        @Test
        void remainingMs_noDeadline_returnsZero() {
            Map<String, Object> submission = new LinkedHashMap<>();

            assertEquals(0L, examService.remainingMs(submission));
        }
    }

    // ================================================================
    // Ensure Student Session tests
    // ================================================================

    @Nested
    class EnsureStudentSessionTests {

        @Test
        void ensureStudentSession_newSession_createsSessionWithShuffledOrder() {
            Store store = createEmptyStore();

            Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
            Map<String, Object> exam = makeExam("e1", "p1", start, end, true, List.of("c1"));
            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1", "q2", "q3"));
            store.papers.add(paper);

            Map<String, Object> q1 = makeQuestion("q1", "Question 1", List.of("A", "B", "C"), List.of("A"), 10);
            Map<String, Object> q2 = makeQuestion("q2", "Question 2", List.of("A", "B"), List.of("B"), 10);
            Map<String, Object> q3 = makeQuestion("q3", "Question 3", List.of("A", "B", "C", "D"), List.of("C"), 10);
            store.questions.addAll(List.of(q1, q2, q3));

            Map<String, Object> student = makeUser("s1", "student", "Alice", "c1");

            Map<String, Object> session = examService.ensureStudentSession(store, exam, student);

            assertNull(session.get("error"));
            assertEquals("e1", session.get("examId"));
            assertEquals("s1", session.get("studentId"));
            assertEquals("Alice", session.get("studentName"));
            assertEquals("进行中", session.get("status"));
            assertNotNull(session.get("id"));
            assertNotNull(session.get("startedAt"));
            assertNotNull(session.get("deadlineAt"));
            assertNotNull(session.get("updatedAt"));

            // Question order should contain all 3 question IDs
            @SuppressWarnings("unchecked")
            List<String> questionOrder = (List<String>) session.get("questionOrder");
            assertNotNull(questionOrder);
            assertEquals(3, questionOrder.size());
            assertTrue(questionOrder.containsAll(List.of("q1", "q2", "q3")));

            // Option order should be generated for questions with >1 option
            @SuppressWarnings("unchecked")
            Map<String, Object> optionOrder = (Map<String, Object>) session.get("optionOrder");
            assertNotNull(optionOrder);
            assertTrue(optionOrder.containsKey("q1"));
            assertTrue(optionOrder.containsKey("q2"));
            assertTrue(optionOrder.containsKey("q3"));
        }

        @Test
        void ensureStudentSession_completedSubmission_returnsError() {
            Store store = createEmptyStore();

            Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
            Map<String, Object> exam = makeExam("e1", "p1", start, end, true, List.of("c1"));

            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1"));
            store.papers.add(paper);

            // Existing completed submission
            Map<String, Object> submission = new LinkedHashMap<>();
            submission.put("id", "sub1");
            submission.put("examId", "e1");
            submission.put("studentId", "s1");
            submission.put("status", "已完成");
            store.submissions.add(submission);

            Map<String, Object> student = makeUser("s1", "student", "Alice", "c1");

            Map<String, Object> session = examService.ensureStudentSession(store, exam, student);

            assertNotNull(session.get("error"));
            assertTrue(session.get("error").toString().contains("Submission already finished"));
        }

        @Test
        void ensureStudentSession_examNotStarted_returnsError() {
            Store store = createEmptyStore();

            Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
            Map<String, Object> exam = makeExam("e1", "p1", start, end, true, List.of("c1"));

            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1"));
            store.papers.add(paper);

            Map<String, Object> student = makeUser("s1", "student", "Alice", "c1");

            Map<String, Object> session = examService.ensureStudentSession(store, exam, student);

            assertNotNull(session.get("error"));
            assertEquals("Exam has not started.", session.get("error"));
        }

        @Test
        void ensureStudentSession_examEnded_returnsError() {
            Store store = createEmptyStore();

            Instant start = Instant.now().minus(2, ChronoUnit.HOURS);
            Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
            Map<String, Object> exam = makeExam("e1", "p1", start, end, true, List.of("c1"));

            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1"));
            store.papers.add(paper);

            Map<String, Object> student = makeUser("s1", "student", "Alice", "c1");

            Map<String, Object> session = examService.ensureStudentSession(store, exam, student);

            assertNotNull(session.get("error"));
            assertEquals("已结束", session.get("error"));
        }

        @Test
        void ensureStudentSession_paperNotFound_returnsError() {
            Store store = createEmptyStore();

            Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
            Instant end = Instant.now().plus(2, ChronoUnit.HOURS);
            Map<String, Object> exam = makeExam("e1", "nonexistent_paper", start, end, true, List.of("c1"));
            // No paper added to store

            Map<String, Object> student = makeUser("s1", "student", "Alice", "c1");

            Map<String, Object> session = examService.ensureStudentSession(store, exam, student);

            assertNotNull(session.get("error"));
            assertEquals("Paper not found.", session.get("error"));
        }
    }

    // ================================================================
    // Build Exam Snapshot tests
    // ================================================================

    @Nested
    class BuildSnapshotTests {

        @Test
        void buildExamSnapshot_hideAnswersTrue_removesAnswerField() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1", "q2"));
            store.papers.add(paper);

            Map<String, Object> q1 = makeQuestion("q1", "What is 1+1?", List.of("1", "2", "3"), List.of("2"), 50);
            Map<String, Object> q2 = makeQuestion("q2", "What is 2+2?", List.of("3", "4", "5"), List.of("4"), 50);
            store.questions.addAll(List.of(q1, q2));

            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of());

            Map<String, Object> snapshot = examService.buildExamSnapshot(store, exam, true, null, null);

            assertNotNull(snapshot.get("paper"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) snapshot.get("questions");
            assertEquals(2, questions.size());

            // Answers should be hidden
            for (Map<String, Object> q : questions) {
                assertNull(q.get("answer"), "Answer field should be removed when hideAnswers=true");
                assertNotNull(q.get("order"), "Each question should have an order number");
            }
            assertEquals(1, questions.get(0).get("order"));
            assertEquals(2, questions.get(1).get("order"));
        }

        @Test
        void buildExamSnapshot_hideAnswersFalse_keepsAnswerField() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1"));
            store.papers.add(paper);

            Map<String, Object> q1 = makeQuestion("q1", "What is 1+1?", List.of("1", "2", "3"), List.of("2"), 100);
            store.questions.add(q1);

            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of());

            Map<String, Object> snapshot = examService.buildExamSnapshot(store, exam, false, null, null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) snapshot.get("questions");
            assertEquals(1, questions.size());
            assertNotNull(questions.get(0).get("answer"), "Answer field should be present when hideAnswers=false");
        }

        @Test
        void buildExamSnapshot_withCustomQuestionOrder_usesProvidedOrder() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1", "q2", "q3"));
            store.papers.add(paper);

            store.questions.add(makeQuestion("q1", "Q1", List.of("A"), List.of("A"), 10));
            store.questions.add(makeQuestion("q2", "Q2", List.of("B"), List.of("B"), 10));
            store.questions.add(makeQuestion("q3", "Q3", List.of("C"), List.of("C"), 10));

            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of());

            // Custom order: q3, q1, q2
            List<String> customOrder = List.of("q3", "q1", "q2");

            Map<String, Object> snapshot = examService.buildExamSnapshot(store, exam, true, customOrder, null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) snapshot.get("questions");
            assertEquals(3, questions.size());
            assertEquals("q3", questions.get(0).get("id"));
            assertEquals("q1", questions.get(1).get("id"));
            assertEquals("q2", questions.get(2).get("id"));
        }

        @Test
        void buildExamSnapshot_withOptionOrder_shufflesOptions() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 90, 100, 60, List.of("q1"));
            store.papers.add(paper);

            Map<String, Object> q1 = makeQuestion("q1", "Pick one", List.of("A", "B", "C", "D"), List.of("A"), 100);
            store.questions.add(q1);

            Map<String, Object> exam = makeExam("e1", "p1",
                    Instant.now().minus(1, ChronoUnit.HOURS),
                    Instant.now().plus(1, ChronoUnit.HOURS),
                    true, List.of());

            // Force a specific shuffle order: indices [3, 1, 0, 2] means D, B, A, C
            Map<String, Object> optionOrder = new LinkedHashMap<>();
            optionOrder.put("q1", List.of(3, 1, 0, 2));

            Map<String, Object> snapshot = examService.buildExamSnapshot(store, exam, false, null, optionOrder);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) snapshot.get("questions");
            assertEquals(1, questions.size());
            @SuppressWarnings("unchecked")
            List<Object> options = (List<Object>) questions.get(0).get("options");
            assertEquals(List.of("D", "B", "A", "C"), options);
        }
    }

    // ================================================================
    // Student Submission lookup tests
    // ================================================================

    @Nested
    class StudentSubmissionTests {

        @Test
        void studentSubmission_found_returnsSubmission() {
            Store store = createEmptyStore();
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("id", "sub1");
            sub.put("examId", "e1");
            sub.put("studentId", "s1");
            sub.put("status", "进行中");
            store.submissions.add(sub);

            Map<String, Object> result = examService.studentSubmission(store, "e1", "s1");

            assertNotNull(result);
            assertEquals("sub1", result.get("id"));
        }

        @Test
        void studentSubmission_notFound_returnsNull() {
            Store store = createEmptyStore();

            assertNull(examService.studentSubmission(store, "e1", "s1"));
        }

        @Test
        void studentSubmission_wrongExam_returnsNull() {
            Store store = createEmptyStore();
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("id", "sub1");
            sub.put("examId", "e1");
            sub.put("studentId", "s1");
            store.submissions.add(sub);

            assertNull(examService.studentSubmission(store, "e999", "s1"));
        }
    }
}
