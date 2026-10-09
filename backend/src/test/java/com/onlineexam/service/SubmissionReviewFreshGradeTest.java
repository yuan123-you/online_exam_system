package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionReviewFreshGradeTest {
  private final SubmissionService service = new SubmissionService(mock(StoreService.class), mock(ExamService.class),
      mock(SystemLogService.class), mock(WrongBookService.class));
  private Store store() {
    var store = new Store();
    store.exams.add(Map.of("id", "e1", "paperId", "p1", "published", true));
    store.examSnapshots.put("e1", Map.of("schemaVersion", 1, "paper", Map.of("id", "p1", "questionIds", List.of("q1")),
        "questions", List.of(Map.of("id", "q1"))));
    store.submissions.add(Map.of("id", "s2", "examId", "e1", "status", "已完成", "finalScore", 8));
    return store;
  }
  private Map<String,Object> newlySaved() {
    return Map.of("id", "s1", "examId", "e1", "status", "已完成", "finalScore", 10);
  }
  @ParameterizedTest @ValueSource(strings={"待阅卷", "已完成"})
  void newlySavedGradeOverridesStaleRowForRankWithoutMutatingStore(String previousStatus) {
    var store=store();
    var stale=new LinkedHashMap<String,Object>(Map.of("id","s1","examId","e1","status",previousStatus,"finalScore",0));
    store.submissions.add(stale);
    var review=service.buildSubmissionReview(store,newlySaved());
    assertEquals(1,review.get("rank")); assertEquals(2,review.get("finishedCount"));
    assertEquals(2L,review.get("participantCount")); assertEquals(0,stale.get("finalScore"));
    assertEquals(previousStatus,stale.get("status"));
  }
  @Test void firstSubmissionNotYetInReadViewIsIncludedExactlyOnce() {
    var store=store(); var review=service.buildSubmissionReview(store,newlySaved());
    assertEquals(1,review.get("rank")); assertEquals(2,review.get("finishedCount"));
    assertEquals(2L,review.get("participantCount")); assertEquals(1,store.submissions.size());
  }
  @Test void unchangedExistingReviewDoesNotDoubleCountSubmission() {
    var store=store(); store.submissions.add(newlySaved());
    var review=service.buildSubmissionReview(store,newlySaved());
    assertEquals(1,review.get("rank")); assertEquals(2,review.get("finishedCount"));
    assertEquals(2L,review.get("participantCount"));
  }
}
