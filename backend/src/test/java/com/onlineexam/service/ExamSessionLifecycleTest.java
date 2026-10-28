package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.controller.ExamController;
import com.onlineexam.controller.SubmissionController;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamSessionLifecycleTest {
  private final StoreService storage = mock(StoreService.class);
  private final SystemLogService logs = mock(SystemLogService.class);
  private final StoreService.Store store = new StoreService.Store();
  private final ExamService exams = new ExamService(storage, logs);
  private Map<String, Object> exam;
  private Map<String, Object> student;
  private Map<String, Object> submission;

  @BeforeEach void setUp() {
    Instant now = Instant.now();
    student = Map.of("id", "u1", "name", "Student", "role", "student", "classId", "c1");
    exam = Map.of("id", "e1", "paperId", "p1", "published", true, "targetClassIds", List.of("c1"),
        "startTime", now.minusSeconds(7200).toString(), "endTime", now.minusSeconds(3600).toString());
    submission = new LinkedHashMap<>(Map.of("id", "s1", "examId", "e1", "studentId", "u1", "status", "进行中",
        "startedAt", now.minusSeconds(7200).toString(), "deadlineAt", now.plusSeconds(600).toString(),
        "manualExtendedMinutes", 70, "answers", List.of()));
    store.users.add(student);
    store.exams.add(exam);
    store.submissions.add(submission);
    store.examSnapshots.put("e1", Map.of("schemaVersion", 1,
        "paper", Map.of("id", "p1", "name", "Paper", "durationMinutes", 60, "questionIds", List.of("q1"), "totalScore", 100, "passScore", 60),
        "questions", List.of(Map.of("id", "q1", "title", "Question", "type", "single", "options", List.of("A", "B"), "answer", List.of("A"), "score", 100))));
    when(storage.readStore()).thenReturn(store);
  }

  @Test void unexpiredTeacherExtensionRemainsUsableAfterTheGlobalEnd() {
    var before = new LinkedHashMap<>(submission);
    var session = exams.ensureStudentSession(store, exam, student);
    assertFalse(session.containsKey("error"));
    assertEquals(submission.get("deadlineAt"), session.get("deadlineAt"));
    assertEquals("进行中", session.get("status"));
    assertEquals(before, submission);
    verify(storage, never()).saveRecord(anyString(), anyMap());
  }

  @Test void expiredExtensionCannotResumeTheExam() {
    submission.put("deadlineAt", Instant.now().minusSeconds(60).toString());
    assertEquals("已结束", exams.ensureStudentSession(store, exam, student).get("error"));
  }

  @Test void aFutureDeadlineWithoutManualExtensionCannotReopenTheExam() {
    submission.remove("manualExtendedMinutes");
    assertEquals("已结束", exams.ensureStudentSession(store, exam, student).get("error"));
  }

  @Test void noNewSessionCanStartAfterTheGlobalEnd() {
    store.submissions.clear();
    assertEquals("已结束", exams.ensureStudentSession(store, exam, student).get("error"));
    assertTrue(store.submissions.isEmpty());
  }

  @Test void completedSubmissionStillReportsFinishedAfterTheGlobalEnd() {
    submission.put("status", "已完成");
    assertEquals("Submission already finished.", exams.ensureStudentSession(store, exam, student).get("error"));
  }

  @Test void pendingSubmissionStillReportsFinishedAfterTheGlobalEnd() {
    submission.put("status", "待阅卷");
    assertEquals("Submission already finished.", exams.ensureStudentSession(store, exam, student).get("error"));
  }

  @Test void completedExamDetailReturnsTheExistingFinishedSessionContract() {
    submission.put("status", "已完成");
    var controller = new ExamController(storage, exams, mock(SubmissionService.class), logs, mock(ExcelExportService.class));
    var response = controller.examDetail("u1", "e1");
    assertEquals(200, response.getStatusCode().value());
    assertEquals("finished", ((Map<?, ?>) response.getBody()).get("sessionState"));
    verify(storage, never()).saveRecord(anyString(), anyMap());
  }

  @Test void extendedStudentCanSaveAnswersAfterTheGlobalEnd() {
    var controller = new SubmissionController(storage, exams, mock(SubmissionService.class), logs);
    var response = controller.saveSubmission("u1", Map.of("examId", "e1", "answers", List.of(Map.of("questionId", "q1", "answer", List.of("A")))));
    assertEquals(200, response.getStatusCode().value());
    verify(storage).saveStudentSubmission(argThat(record -> record.get("deadlineAt").equals(submission.get("deadlineAt"))));
  }

  @Test void extendedStudentCanSubmitBeforeTheirIndividualDeadline() {
    var grading = new SubmissionService(storage, exams, logs, mock(WrongBookService.class));
    var controller = new SubmissionController(storage, exams, grading, logs);
    var response = controller.submitSubmission("u1", Map.of("examId", "e1", "answers", List.of(Map.of("questionId", "q1", "answer", List.of("A")))));
    assertEquals(200, response.getStatusCode().value());
    var review = (Map<?, ?>) ((Map<?, ?>) response.getBody()).get("submission");
    assertEquals(100, review.get("finalScore"));
    assertEquals("已完成", review.get("status"));
    assertEquals("进行中", submission.get("status"));
  }
}
