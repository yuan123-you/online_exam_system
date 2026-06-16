package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private StoreService storeService;

    @InjectMocks
    private AnalysisService analysisService;

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

    private Map<String, Object> makeUser(String id, String role, String name, String classId) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("role", role);
        user.put("name", name);
        user.put("classId", classId);
        return user;
    }

    private Map<String, Object> makeSubmission(String id, String studentId, String examId,
                                                String status, int finalScore,
                                                String submittedAt, List<Object> answerDetail) {
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("id", id);
        sub.put("studentId", studentId);
        sub.put("examId", examId);
        sub.put("status", status);
        sub.put("finalScore", finalScore);
        sub.put("submittedAt", submittedAt);
        sub.put("answerDetail", answerDetail);
        return sub;
    }

    private Map<String, Object> makeExam(String id, String teacherId, String paperId, String name) {
        Map<String, Object> exam = new LinkedHashMap<>();
        exam.put("id", id);
        exam.put("teacherId", teacherId);
        exam.put("paperId", paperId);
        exam.put("name", name);
        return exam;
    }

    private Map<String, Object> makePaper(String id, int totalScore, int passScore, List<Object> questionIds) {
        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("id", id);
        paper.put("totalScore", totalScore);
        paper.put("passScore", passScore);
        paper.put("questionIds", questionIds);
        return paper;
    }

    private Map<String, Object> makeQuestion(String id, String title, String type,
                                              String subject, String knowledgePoint) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", id);
        q.put("title", title);
        q.put("type", type);
        q.put("subject", subject);
        q.put("knowledgePoint", knowledgePoint);
        return q;
    }

    private Map<String, Object> makeAnswerDetail(String questionId, String subject,
                                                  String knowledgePoint, boolean correct) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("questionId", questionId);
        detail.put("subject", subject);
        detail.put("knowledgePoint", knowledgePoint);
        detail.put("correct", correct);
        return detail;
    }

    // ================================================================
    // ScoreTrend tests
    // ================================================================

    @Nested
    class ScoreTrendTests {

        @Test
        void studentWithCompletedSubmissions_returnsTrend() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "Alice", "c1"));
            store.exams.add(makeExam("e1", "t1", "p1", "Math Exam"));
            store.papers.add(makePaper("p1", 100, 60, List.of("q1")));
            store.submissions.add(makeSubmission("sub1", "s1", "e1", "已完成", 85, "2025-01-01T10:00:00Z", List.of()));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.scoreTrend("s1", store);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) body.get("trend");
            assertEquals(1, trend.size());
            assertEquals("Math Exam", trend.get(0).get("examName"));
            assertEquals(85, trend.get(0).get("score"));
            assertEquals(100, trend.get(0).get("totalScore"));
            assertEquals(60, trend.get(0).get("passScore"));
            assertEquals("2025-01-01T10:00:00Z", trend.get(0).get("submittedAt"));
        }

        @Test
        void nonStudent_returnsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "Teacher", null));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.scoreTrend("t1", store);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void onlyCompletedSubmissionsIncluded() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "Alice", "c1"));
            store.exams.add(makeExam("e1", "t1", "p1", "Math Exam"));
            store.papers.add(makePaper("p1", 100, 60, List.of("q1")));
            store.exams.add(makeExam("e2", "t1", "p2", "English Exam"));
            store.papers.add(makePaper("p2", 100, 60, List.of("q2")));
            store.submissions.add(makeSubmission("sub1", "s1", "e1", "已完成", 85, "2025-01-01T10:00:00Z", List.of()));
            store.submissions.add(makeSubmission("sub2", "s1", "e2", "进行中", 0, null, List.of()));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.scoreTrend("s1", store);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) body.get("trend");
            assertEquals(1, trend.size());
            assertEquals("Math Exam", trend.get(0).get("examName"));
        }
    }

    // ================================================================
    // KnowledgeRadar tests
    // ================================================================

    @Nested
    class KnowledgeRadarTests {

        @Test
        void studentWithAnswerDetails_returnsMastery() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "Alice", "c1"));
            List<Object> details = List.of(
                makeAnswerDetail("q1", "Math", "Algebra", true),
                makeAnswerDetail("q2", "Math", "Algebra", false),
                makeAnswerDetail("q3", "Math", "Geometry", true)
            );
            store.submissions.add(makeSubmission("sub1", "s1", "e1", "已完成", 80, "2025-01-01T10:00:00Z", details));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.knowledgeRadar("s1", store);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subjectMastery = (List<Map<String, Object>>) body.get("subjectMastery");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> kpMastery = (List<Map<String, Object>>) body.get("knowledgePointMastery");

            assertEquals(1, subjectMastery.size());
            assertEquals("Math", subjectMastery.get(0).get("subject"));
            assertEquals(3, subjectMastery.get(0).get("totalQuestions"));
            assertEquals(2, subjectMastery.get(0).get("correctQuestions"));
            assertEquals(67L, subjectMastery.get(0).get("mastery"));

            assertEquals(2, kpMastery.size());
            // Algebra: 2 total, 1 correct => 50%
            Map<String, Object> algebraKp = kpMastery.stream()
                .filter(kp -> "Algebra".equals(kp.get("knowledgePoint"))).findFirst().orElseThrow();
            assertEquals(2, algebraKp.get("totalQuestions"));
            assertEquals(1, algebraKp.get("correctQuestions"));
            assertEquals(50L, algebraKp.get("mastery"));

            // Geometry: 1 total, 1 correct => 100%
            Map<String, Object> geometryKp = kpMastery.stream()
                .filter(kp -> "Geometry".equals(kp.get("knowledgePoint"))).findFirst().orElseThrow();
            assertEquals(1, geometryKp.get("totalQuestions"));
            assertEquals(1, geometryKp.get("correctQuestions"));
            assertEquals(100L, geometryKp.get("mastery"));
        }

        @Test
        void nonStudent_returnsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "Teacher", null));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.knowledgeRadar("t1", store);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void correctMasteryCalculation() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "Alice", "c1"));
            // 5 questions: 3 correct => 60%
            List<Object> details = List.of(
                makeAnswerDetail("q1", "Physics", "Mechanics", true),
                makeAnswerDetail("q2", "Physics", "Mechanics", true),
                makeAnswerDetail("q3", "Physics", "Optics", true),
                makeAnswerDetail("q4", "Physics", "Optics", false),
                makeAnswerDetail("q5", "Physics", "Optics", false)
            );
            store.submissions.add(makeSubmission("sub1", "s1", "e1", "已完成", 60, "2025-01-01T10:00:00Z", details));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.knowledgeRadar("s1", store);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subjectMastery = (List<Map<String, Object>>) body.get("subjectMastery");

            assertEquals(1, subjectMastery.size());
            assertEquals("Physics", subjectMastery.get(0).get("subject"));
            assertEquals(5, subjectMastery.get(0).get("totalQuestions"));
            assertEquals(3, subjectMastery.get(0).get("correctQuestions"));
            assertEquals(60L, subjectMastery.get(0).get("mastery"));
        }
    }

    // ================================================================
    // QuestionAnalysis tests
    // ================================================================

    @Nested
    class QuestionAnalysisTests {

        @Test
        void teacherOwningExam_getsAnalysis() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "Teacher", null));
            store.exams.add(makeExam("e1", "t1", "p1", "Math Exam"));
            store.papers.add(makePaper("p1", 100, 60, List.of("q1", "q2")));
            store.questions.add(makeQuestion("q1", "Q1 Title", "single", "Math", "Algebra"));
            store.questions.add(makeQuestion("q2", "Q2 Title", "single", "Math", "Geometry"));

            List<Object> details1 = List.of(
                makeAnswerDetail("q1", "Math", "Algebra", true),
                makeAnswerDetail("q2", "Math", "Geometry", false)
            );
            List<Object> details2 = List.of(
                makeAnswerDetail("q1", "Math", "Algebra", true),
                makeAnswerDetail("q2", "Math", "Geometry", true)
            );
            store.submissions.add(makeSubmission("sub1", "s1", "e1", "已完成", 80, "2025-01-01T10:00:00Z", details1));
            store.submissions.add(makeSubmission("sub2", "s2", "e1", "已完成", 90, "2025-01-01T11:00:00Z", details2));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.questionAnalysis("t1", "e1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) body.get("questions");
            assertEquals(2, questions.size());

            // q1: 2 attempts, 2 correct => 100.0%
            Map<String, Object> q1Result = questions.stream()
                .filter(q -> "q1".equals(q.get("questionId"))).findFirst().orElseThrow();
            assertEquals(2, q1Result.get("totalAttempts"));
            assertEquals(2, q1Result.get("correctCount"));
            assertEquals(100.0, q1Result.get("correctRate"));

            // q2: 2 attempts, 1 correct => 50.0%
            Map<String, Object> q2Result = questions.stream()
                .filter(q -> "q2".equals(q.get("questionId"))).findFirst().orElseThrow();
            assertEquals(2, q2Result.get("totalAttempts"));
            assertEquals(1, q2Result.get("correctCount"));
            assertEquals(50.0, q2Result.get("correctRate"));
        }

        @Test
        void nonTeacher_returnsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("s1", "student", "Alice", "c1"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.questionAnalysis("s1", "e1");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void teacherNotOwningExam_returnsForbidden() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "Teacher1", null));
            store.users.add(makeUser("t2", "teacher", "Teacher2", null));
            store.exams.add(makeExam("e1", "t2", "p1", "Math Exam"));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.questionAnalysis("t1", "e1");

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void correctCorrectRateCalculation() {
            Store store = createEmptyStore();
            store.users.add(makeUser("t1", "teacher", "Teacher", null));
            store.exams.add(makeExam("e1", "t1", "p1", "Math Exam"));
            store.papers.add(makePaper("p1", 100, 60, List.of("q1")));
            store.questions.add(makeQuestion("q1", "Q1 Title", "single", "Math", "Algebra"));

            // 3 attempts, 1 correct => 33.3%
            List<Object> details1 = List.of(makeAnswerDetail("q1", "Math", "Algebra", true));
            List<Object> details2 = List.of(makeAnswerDetail("q1", "Math", "Algebra", false));
            List<Object> details3 = List.of(makeAnswerDetail("q1", "Math", "Algebra", false));
            store.submissions.add(makeSubmission("sub1", "s1", "e1", "已完成", 80, "2025-01-01T10:00:00Z", details1));
            store.submissions.add(makeSubmission("sub2", "s2", "e1", "已完成", 60, "2025-01-01T11:00:00Z", details2));
            store.submissions.add(makeSubmission("sub3", "s3", "e1", "已完成", 40, "2025-01-01T12:00:00Z", details3));
            when(storeService.readStore()).thenReturn(store);

            ResponseEntity<?> response = analysisService.questionAnalysis("t1", "e1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) body.get("questions");
            assertEquals(1, questions.size());
            assertEquals(3, questions.get(0).get("totalAttempts"));
            assertEquals(1, questions.get(0).get("correctCount"));
            assertEquals(33.3, questions.get(0).get("correctRate"));

            // KnowledgePoint analysis sorted by correctRate ascending
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> kpAnalysis = (List<Map<String, Object>>) body.get("knowledgePointAnalysis");
            assertEquals(1, kpAnalysis.size());
            assertEquals("Algebra", kpAnalysis.get(0).get("knowledgePoint"));
            assertEquals(3, kpAnalysis.get(0).get("totalAttempts"));
            assertEquals(1, kpAnalysis.get(0).get("correctCount"));
            assertEquals(33.3, kpAnalysis.get(0).get("correctRate"));
        }
    }
}
