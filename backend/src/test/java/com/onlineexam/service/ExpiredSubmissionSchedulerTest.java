package com.onlineexam.service;

import com.onlineexam.StoreService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExpiredSubmissionSchedulerTest {
  @Test void applicationActuallyEnablesTheScheduledExpirySweep() throws Exception {
    assertTrue(com.onlineexam.OnlineExamApplication.class.isAnnotationPresent(org.springframework.scheduling.annotation.EnableScheduling.class));
    var annotation = ExpiredSubmissionScheduler.class.getMethod("settleExpiredSessions")
        .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
    assertNotNull(annotation);
    assertEquals("${exam.expiry-check-delay-ms:5000}", annotation.fixedDelayString());
  }

  @Test void sweepUsesTheSharedPolicyAndContinuesPastAConflictingRecord() {
    var storage = mock(StoreService.class);
    var submissions = mock(SubmissionService.class);
    var store = new StoreService.Store();
    var first = Map.<String,Object>of("id", "first");
    var second = Map.<String,Object>of("id", "second");
    var active = Map.<String,Object>of("id", "active");
    store.submissions.addAll(List.of(first, second, active));
    when(storage.readStore()).thenReturn(store);
    when(submissions.isExpired(store, first)).thenReturn(true);
    when(submissions.isExpired(store, second)).thenReturn(true);
    when(submissions.settleExpiredSubmission(store, first)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
    var scheduler = new ExpiredSubmissionScheduler(storage, submissions);
    assertDoesNotThrow(scheduler::settleExpiredSessions);
    verify(submissions).settleExpiredSubmission(store, second);
    verify(submissions, never()).settleExpiredSubmission(store, active);
  }

  @Test void transientReadFailureDoesNotDisableFutureSweeps() {
    var storage = mock(StoreService.class);
    var submissions = mock(SubmissionService.class);
    var store = new StoreService.Store();
    var row = Map.<String,Object>of("id", "later"); store.submissions.add(row);
    when(storage.readStore()).thenThrow(new IllegalStateException("offline")).thenReturn(store);
    when(submissions.isExpired(store, row)).thenReturn(true);
    var scheduler = new ExpiredSubmissionScheduler(storage, submissions);
    assertDoesNotThrow(scheduler::settleExpiredSessions);
    assertDoesNotThrow(scheduler::settleExpiredSessions);
    verify(submissions).settleExpiredSubmission(store, row);
  }

  @Test void expirationDetectionSkipsFinishedFutureInvalidAndMissingExamSessions() {
    var storage = mock(StoreService.class);
    var logs = mock(SystemLogService.class);
    var service = new SubmissionService(storage, new ExamService(storage, logs), logs, mock(WrongBookService.class));
    var store = new StoreService.Store();
    store.exams.add(Map.of("id", "e1", "endTime", Instant.now().plusSeconds(600).toString()));
    var row = new LinkedHashMap<String,Object>(Map.of("id", "s1", "examId", "e1", "status", "进行中", "deadlineAt", Instant.now().minusSeconds(1).toString()));
    assertTrue(service.isExpired(store, row));
    row.put("status", "已完成"); assertFalse(service.isExpired(store, row));
    row.put("status", "进行中"); row.put("deadlineAt", Instant.now().plusSeconds(60).toString()); assertFalse(service.isExpired(store, row));
    row.put("deadlineAt", "invalid"); assertFalse(service.isExpired(store, row));
    row.put("deadlineAt", Instant.now().minusSeconds(1).toString()); row.put("examId", "missing"); assertFalse(service.isExpired(store, row));
  }
}
