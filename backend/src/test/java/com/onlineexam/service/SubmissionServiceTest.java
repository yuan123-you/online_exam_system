package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.SubmissionService.CompareResult;
import java.time.Instant;
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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private ExamService examService;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private WrongBookService wrongBookService;

    @InjectMocks
    private SubmissionService submissionService;

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

    private Map<String, Object> makeQuestion(String id, String type, List<Object> answer, int score) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("type", type);
        q.put("answer", answer);
        q.put("score", score);
        q.put("title", "Question " + id);
        q.put("subject", "Math");
        q.put("knowledgePoint", "Algebra");
        return q;
    }

    private Map<String, Object> makePaper(String id, int totalScore, int passScore, List<Object> questionIds) {
        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("id", id);
        paper.put("name", "Test Paper");
        paper.put("totalScore", totalScore);
        paper.put("passScore", passScore);
        paper.put("durationMinutes", 90);
        paper.put("questionIds", questionIds);
        return paper;
    }

    private Map<String, Object> makeExam(String id, String paperId) {
        Map<String, Object> exam = new LinkedHashMap<>();
        exam.put("id", id);
        exam.put("paperId", paperId);
        exam.put("name", "Test Exam");
        exam.put("targetClassIds", List.of("c1"));
        return exam;
    }

    private Map<String, Object> makeSubmission(String id, String examId, List<Object> answers) {
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("id", id);
        sub.put("examId", examId);
        sub.put("studentId", "s1");
        sub.put("studentName", "Alice");
        sub.put("answers", answers);
        return sub;
    }

    private Map<String, Object> makeAnswerItem(String questionId, Object answer) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("questionId", questionId);
        item.put("answer", answer instanceof List ? answer : List.of(answer));
        return item;
    }

    // ================================================================
    // Compare tests
    // ================================================================

    @Nested
    class CompareTests {

        @Test
        void compare_singleCorrect_returnsTrue() {
            Map<String, Object> question = makeQuestion("q1", "single", List.of("A"), 10);
            CompareResult result = submissionService.compare(question, List.of("A"), false);
            assertTrue(result.correct());
            assertEquals(List.of("A"), result.answer());
        }

        @Test
        void compare_singleWrong_returnsFalse() {
            Map<String, Object> question = makeQuestion("q1", "single", List.of("A"), 10);
            CompareResult result = submissionService.compare(question, List.of("B"), false);
            assertFalse(result.correct());
        }

        @Test
        void compare_judgeCorrect_returnsTrue() {
            Map<String, Object> question = makeQuestion("q1", "judge", List.of("true"), 10);
            CompareResult result = submissionService.compare(question, List.of("true"), false);
            assertTrue(result.correct());
        }

        @Test
        void compare_judgeWrong_returnsFalse() {
            Map<String, Object> question = makeQuestion("q1", "judge", List.of("true"), 10);
            CompareResult result = submissionService.compare(question, List.of("false"), false);
            assertFalse(result.correct());
        }

        @Test
        void compare_multipleCorrect_returnsTrue() {
            Map<String, Object> question = makeQuestion("q1", "multiple", List.of("A", "B", "C"), 10);
            CompareResult result = submissionService.compare(question, List.of("A", "B", "C"), false);
            assertTrue(result.correct());
        }

        @Test
        void compare_multipleWrong_returnsFalse() {
            Map<String, Object> question = makeQuestion("q1", "multiple", List.of("A", "B", "C"), 10);
            CompareResult result = submissionService.compare(question, List.of("A", "B"), false);
            assertFalse(result.correct());
        }

        @Test
        void compare_multipleSizeMismatch_returnsFalse() {
            Map<String, Object> question = makeQuestion("q1", "multiple", List.of("A", "B"), 10);
            CompareResult result = submissionService.compare(question, List.of("A", "B", "A"), false);
            assertFalse(result.correct());
        }

        @Test
        void compare_fillCorrect_withWhitespaceDifferences_returnsTrue() {
            Map<String, Object> question = makeQuestion("q1", "fill", List.of("hello world"), 10);
            CompareResult result = submissionService.compare(question, List.of("  Hello   World  "), false);
            assertTrue(result.correct());
        }

        @Test
        void compare_fillWrong_returnsFalse() {
            Map<String, Object> question = makeQuestion("q1", "fill", List.of("hello world"), 10);
            CompareResult result = submissionService.compare(question, List.of("goodbye"), false);
            assertFalse(result.correct());
        }

        @Test
        void compare_unknownType_returnsNullCorrect() {
            Map<String, Object> question = makeQuestion("q1", "essay", List.of("some answer"), 10);
            CompareResult result = submissionService.compare(question, List.of("some answer"), false);
            assertNull(result.correct());
        }

        @Test
        void compare_subjectiveWithAutoCorrect_returnsTrue() {
            Map<String, Object> question = makeQuestion("q1", "short", List.of("the answer is 42"), 10);
            CompareResult result = submissionService.compare(question, List.of("the answer is 42"), true);
            assertTrue(result.correct());
        }

        @Test
        void compare_subjectiveWithAutoWrong_returnsFalse() {
            Map<String, Object> question = makeQuestion("q1", "short", List.of("the answer is 42"), 10);
            CompareResult result = submissionService.compare(question, List.of("wrong answer"), true);
            assertFalse(result.correct());
        }

        @Test
        void compare_subjectiveWithoutAuto_returnsNullCorrect() {
            Map<String, Object> question = makeQuestion("q1", "short", List.of("the answer is 42"), 10);
            CompareResult result = submissionService.compare(question, List.of("the answer is 42"), false);
            assertNull(result.correct());
        }
    }

    // ================================================================
    // NormalizeAnswer tests
    // ================================================================

    @Nested
    class NormalizeAnswerTests {

        @Test
        void normalizeAnswer_listInput_filtersNullAndBlank() {
            List<Object> input = new ArrayList<>();
            input.add("A");
            input.add(null);
            input.add("  ");
            input.add("B");
            List<String> result = submissionService.normalizeAnswer(input);
            assertEquals(List.of("A", "B"), result);
        }

        @Test
        void normalizeAnswer_listInput_trimsEach() {
            List<String> result = submissionService.normalizeAnswer(List.of("  A  ", " B "));
            assertEquals(List.of("A", "B"), result);
        }

        @Test
        void normalizeAnswer_singleValue_wrapsInList() {
            List<String> result = submissionService.normalizeAnswer("hello");
            assertEquals(List.of("hello"), result);
        }

        @Test
        void normalizeAnswer_null_returnsEmptyList() {
            List<String> result = submissionService.normalizeAnswer(null);
            assertTrue(result.isEmpty());
        }

        @Test
        void normalizeAnswer_blankString_returnsEmptyList() {
            List<String> result = submissionService.normalizeAnswer("   ");
            assertTrue(result.isEmpty());
        }
    }

    // ================================================================
    // Norm tests
    // ================================================================

    @Nested
    class NormTests {

        @Test
        void norm_normalString_trimsAndLowercases() {
            assertEquals("hello", submissionService.norm("Hello"));
        }

        @Test
        void norm_null_returnsEmpty() {
            assertEquals("", submissionService.norm(null));
        }

        @Test
        void norm_extraWhitespace_collapsesWhitespace() {
            assertEquals("hello world", submissionService.norm("  hello   world  "));
        }

        @Test
        void norm_mixedCase_convertsToLowercase() {
            assertEquals("hello world", submissionService.norm("HeLLo WoRLD"));
        }
    }

    // ================================================================
    // FormatUsedMs tests
    // ================================================================

    @Nested
    class FormatUsedMsTests {

        @Test
        void formatUsedMs_secondsOnly_returnsSeconds() {
            assertEquals("45秒", submissionService.formatUsedMs(45_000));
        }

        @Test
        void formatUsedMs_minutesAndSeconds_returnsMinutesAndSeconds() {
            assertEquals("5分30秒", submissionService.formatUsedMs(330_000));
        }

        @Test
        void formatUsedMs_hoursMinutesSeconds_returnsFullFormat() {
            assertEquals("1小时30分45秒", submissionService.formatUsedMs(5445_000));
        }

        @Test
        void formatUsedMs_zero_returnsZeroSeconds() {
            assertEquals("0秒", submissionService.formatUsedMs(0));
        }
    }

    // ================================================================
    // GradeSubmission tests
    // ================================================================

    @Nested
    class GradeSubmissionTests {

        @Test
        void gradeSubmission_allObjectiveCorrect_setsCompletedStatus() {
            Store store = createEmptyStore();
            Map<String, Object> q1 = makeQuestion("q1", "single", List.of("A"), 50);
            Map<String, Object> q2 = makeQuestion("q2", "judge", List.of("true"), 50);
            store.questions.addAll(List.of(q1, q2));

            Map<String, Object> paper = makePaper("p1", 100, 60, List.of("q1", "q2"));
            store.papers.add(paper);

            Map<String, Object> exam = makeExam("e1", "p1");
            store.exams.add(exam);

            Map<String, Object> submission = makeSubmission("sub1", "e1",
                List.of(makeAnswerItem("q1", "A"), makeAnswerItem("q2", "true")));

            submissionService.gradeSubmission(store, submission);

            assertEquals("已完成", submission.get("status"));
            assertEquals(100, submission.get("autoScore"));
            assertEquals(100, submission.get("finalScore"));
            assertNotNull(submission.get("submittedAt"));
            assertNotNull(submission.get("updatedAt"));
            assertNotNull(submission.get("answerDetail"));
        }

        @Test
        void gradeSubmission_hasSubjective_setsPendingStatus() {
            Store store = createEmptyStore();
            Map<String, Object> q1 = makeQuestion("q1", "single", List.of("A"), 50);
            Map<String, Object> q2 = makeQuestion("q2", "short", List.of("essay answer"), 50);
            store.questions.addAll(List.of(q1, q2));

            Map<String, Object> paper = makePaper("p1", 100, 60, List.of("q1", "q2"));
            store.papers.add(paper);

            Map<String, Object> exam = makeExam("e1", "p1");
            store.exams.add(exam);

            Map<String, Object> submission = makeSubmission("sub1", "e1",
                List.of(makeAnswerItem("q1", "A"), makeAnswerItem("q2", "my essay")));

            submissionService.gradeSubmission(store, submission);

            assertEquals("待阅卷", submission.get("status"));
            assertEquals(50, submission.get("autoScore"));
            assertEquals(50, submission.get("finalScore"));
        }

        @Test
        void gradeSubmission_wrongObjectiveAnswers_notScored() {
            Store store = createEmptyStore();
            Map<String, Object> q1 = makeQuestion("q1", "single", List.of("A"), 50);
            Map<String, Object> q2 = makeQuestion("q2", "judge", List.of("true"), 50);
            store.questions.addAll(List.of(q1, q2));

            Map<String, Object> paper = makePaper("p1", 100, 60, List.of("q1", "q2"));
            store.papers.add(paper);

            Map<String, Object> exam = makeExam("e1", "p1");
            store.exams.add(exam);

            Map<String, Object> submission = makeSubmission("sub1", "e1",
                List.of(makeAnswerItem("q1", "B"), makeAnswerItem("q2", "false")));

            submissionService.gradeSubmission(store, submission);

            assertEquals("已完成", submission.get("status"));
            assertEquals(0, submission.get("autoScore"));
            assertEquals(0, submission.get("finalScore"));
        }
    }

    // ================================================================
    // BuildSubmissionReview tests
    // ================================================================

    @Nested
    class BuildSubmissionReviewTests {

        @Test
        void buildSubmissionReview_scoreAbovePassScore_returnsPassed() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 100, 60, List.of());
            store.papers.add(paper);

            Map<String, Object> exam = makeExam("e1", "p1");
            store.exams.add(exam);

            Map<String, Object> submission = new LinkedHashMap<>();
            submission.put("id", "sub1");
            submission.put("examId", "e1");
            submission.put("status", "已完成");
            submission.put("finalScore", 80);
            store.submissions.add(submission);

            Map<String, Object> review = submissionService.buildSubmissionReview(store, submission);

            assertEquals("已及格", review.get("passStatus"));
            assertEquals("Test Exam", review.get("examName"));
            assertEquals("Test Paper", review.get("paperName"));
            assertEquals(100, review.get("totalScore"));
            assertEquals(60, review.get("passScore"));
            assertNotNull(review.get("scoreRate"));
        }

        @Test
        void buildSubmissionReview_scoreBelowPassScore_returnsFailed() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 100, 60, List.of());
            store.papers.add(paper);

            Map<String, Object> exam = makeExam("e1", "p1");
            store.exams.add(exam);

            Map<String, Object> submission = new LinkedHashMap<>();
            submission.put("id", "sub1");
            submission.put("examId", "e1");
            submission.put("status", "已完成");
            submission.put("finalScore", 40);
            store.submissions.add(submission);

            Map<String, Object> review = submissionService.buildSubmissionReview(store, submission);

            assertEquals("未及格", review.get("passStatus"));
        }

        @Test
        void buildSubmissionReview_pendingStatus_returnsPending() {
            Store store = createEmptyStore();
            Map<String, Object> paper = makePaper("p1", 100, 60, List.of());
            store.papers.add(paper);

            Map<String, Object> exam = makeExam("e1", "p1");
            store.exams.add(exam);

            Map<String, Object> submission = new LinkedHashMap<>();
            submission.put("id", "sub1");
            submission.put("examId", "e1");
            submission.put("status", "待阅卷");
            submission.put("finalScore", 50);
            store.submissions.add(submission);

            Map<String, Object> review = submissionService.buildSubmissionReview(store, submission);

            assertEquals("待定", review.get("passStatus"));
        }
    }
}
