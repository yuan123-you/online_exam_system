package com.onlineexam.controller;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import com.onlineexam.service.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManualGradingIntegrityTest {
  StoreService storage;
  SubmissionService grading;
  SystemLogService logs;
  Store store;
  SubmissionController controller;
  Map<String,Object> submission;
  @BeforeEach void setUp() {
    storage=mock(StoreService.class); grading=mock(SubmissionService.class); logs=mock(SystemLogService.class);
    controller=new SubmissionController(storage,mock(ExamService.class),grading,logs);
    store=new Store(); when(storage.readStore()).thenReturn(store);
    store.users.add(Map.of("id","t1","role","teacher","name","Teacher"));
    store.users.add(Map.of("id","t2","role","teacher"));
    store.exams.add(Map.of("id","e1","teacherId","t1","paperId","p1","published",true));
    store.examSnapshots.put("e1",Map.of("schemaVersion",1,"paper",Map.of("id","p1","questionIds",List.of("q1","q2")),
        "questions",List.of(Map.of("id","q1","score",10),Map.of("id","q2","score",3))));
    submission=new LinkedHashMap<>(Map.of("id","s1","examId","e1","status","待阅卷","finalScore",3,
        "answerDetail",List.of(Map.of("questionId","q1","fullScore",10,"score",0),Map.of("questionId","q2","fullScore",3,"score",3))));
    store.submissions.add(submission);
  }
  private org.springframework.http.ResponseEntity<?> grade() {
    return controller.manualGrade("t1",Map.of("submissionId","s1","scores",Map.of("q1",7)));
  }
  static Stream<String> unsubmitted() { return Stream.of("进行中","已结束","","unknown"); }
  @ParameterizedTest @MethodSource("unsubmitted") void unsubmittedCannotBecomeCompleted(String status) {
    submission.put("status",status);
    assertEquals(HttpStatus.CONFLICT,grade().getStatusCode());
    assertEquals(status,submission.get("status")); assertEquals(3,submission.get("finalScore"));
    verify(storage,never()).saveRecord(anyString(),anyMap()); verifyNoInteractions(grading,logs);
  }
  @Test void unknownHistoricalVersionCannotBeNewlyGraded() {
    store.examSnapshots.clear();
    assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,this::grade).getStatusCode());
    assertEquals(3,submission.get("finalScore")); verify(storage,never()).saveRecord(anyString(),anyMap());
  }
  @Test void failedDatabaseSaveDoesNotMutateSharedSubmission() {
    var before=new LinkedHashMap<>(submission);
    doThrow(new IllegalStateException("database unavailable")).when(storage).saveRecord(eq("submissions"),anyMap());
    assertThrows(IllegalStateException.class,this::grade);
    assertEquals(before,submission); verifyNoInteractions(grading,logs);
  }
  @Test void partialScoringAndCompletedRegradeRemainSupported() {
    submission.put("status","已完成");
    assertEquals(HttpStatus.OK,grade().getStatusCode());
    var saved=org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(storage).saveRecord(eq("submissions"),saved.capture());
    assertEquals(10,saved.getValue().get("finalScore")); assertEquals("已完成",saved.getValue().get("status"));
    assertEquals("Teacher",saved.getValue().get("gradedBy"));
  }
  @Test void otherTeacherCannotGradeSubmission() {
    assertEquals(HttpStatus.FORBIDDEN,controller.manualGrade("t2",Map.of("submissionId","s1","scores",Map.of())).getStatusCode());
    verify(storage,never()).saveRecord(anyString(),anyMap());
  }
}
