package com.onlineexam.controller;
import com.onlineexam.StoreService;
import com.onlineexam.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ManualGradeInputTest {
  StoreService storage;SubmissionService reviews;SystemLogService logs;SubmissionController controller;Map<String,Object> submission; StoreService.Store view;
  @BeforeEach void setup(){
    storage=mock(StoreService.class);reviews=mock(SubmissionService.class);logs=mock(SystemLogService.class);
    view=new StoreService.Store();view.users.add(Map.of("id","t1","role","teacher","name","Teacher"));
    view.exams.add(Map.of("id","e1","paperId","p1","teacherId","t1","published",true));
    view.examSnapshots.put("e1",Map.of("schemaVersion",1,"paper",Map.of("id","p1","questionIds",List.of("q1","q2")),"questions",List.of(Map.of("id","q1","score",10),Map.of("id","q2","score",3))));
    submission=new LinkedHashMap<>(Map.of("id","s1","examId","e1","status","待阅卷","finalScore",3,"answerDetail",List.of(
        Map.of("questionId","q1","fullScore",10,"score",0),Map.of("questionId","q2","fullScore",3,"score",3))));
    view.submissions.add(submission);when(storage.readStore()).thenReturn(view);
    controller=new SubmissionController(storage,mock(ExamService.class),reviews,logs);
  }
  org.springframework.http.ResponseEntity<?> grade(Object scores){
    var body=new LinkedHashMap<String,Object>();body.put("submissionId","s1");body.put("scores",scores);return controller.manualGrade("t1",body);
  }
  static Stream<Object> invalidRoots(){return Stream.of("bad",List.of(7),7);}
  @ParameterizedTest @MethodSource("invalidRoots") void scoreRootMustBeObject(Object input){reject(input);}
  @Test void explicitNullAndMissingScoreObjectRejectBeforeWrite(){reject(null);
    assertEquals(HttpStatus.BAD_REQUEST,controller.manualGrade("t1",Map.of("submissionId","s1")).getStatusCode());}
  static Stream<Object> invalidScores(){return Stream.of(-1,11,2.5,4294967297L,"not score",true,List.of(7),new BigDecimal("2.000000000000000000001"));}
  @ParameterizedTest @MethodSource("invalidScores") void scoreCannotBeTruncatedClampedOrCoercedToZero(Object value){reject(Map.of("q1",value));}
  @Test void explicitNullScoreIsNotAnImplicitZero(){var scores=new LinkedHashMap<String,Object>();scores.put("q1",null);reject(scores);}
  @Test void unknownQuestionMustNotBeSilentlyIgnored(){reject(Map.of("unknown",7));}
  void reject(Object scores){
    var before=new LinkedHashMap<>(submission);assertEquals(HttpStatus.BAD_REQUEST,grade(scores).getStatusCode());
    assertEquals(before,submission);verify(storage,never()).saveRecord(anyString(),anyMap());verifyNoInteractions(reviews,logs);
  }
  static Stream<Object> corruptDetails(){return Stream.of(List.of(),"invalid",List.of(Map.of("fullScore",10,"score",0)),
      List.of(Map.of("questionId","q1","fullScore",10,"score",0),Map.of("questionId","q1","fullScore",10,"score",0)),
      List.of(Map.of("questionId","q1","fullScore",-1,"score",0)),List.of(Map.of("questionId","q1","fullScore",10,"score",11)));}
  @ParameterizedTest @MethodSource("corruptDetails") void corruptStoredDetailCannotAuthorizeCompletedGrade(Object details){
    submission.put("answerDetail",details);var before=new LinkedHashMap<>(submission);
    assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->grade(Map.of())).getStatusCode());
    assertEquals(before,submission);verify(storage,never()).saveRecord(anyString(),anyMap());verifyNoInteractions(reviews,logs);
  }
  static Stream<Object> corruptUnaffectedScores(){return Stream.of(-1,11,2.5,"invalid",(Object)null);}
  @ParameterizedTest @MethodSource("corruptUnaffectedScores")
  void fullCardinalityUnaffectedBadScoreCannotBecomeFinalGrade(Object oldScore){
    var q1=new LinkedHashMap<String,Object>(Map.of("questionId","q1","fullScore",10));q1.put("score",oldScore);
    submission.put("answerDetail",List.of(q1,Map.of("questionId","q2","fullScore",3,"score",3)));
    var before=new LinkedHashMap<>(submission);
    assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->grade(Map.of())).getStatusCode());
    assertEquals(before,submission);verify(storage,never()).saveRecord(anyString(),anyMap());verifyNoInteractions(reviews,logs);
  }
  @ParameterizedTest @ValueSource(strings={"0","7","10"}) void validPartialScoreKeepsUnchangedOtherScore(String score){
    assertEquals(HttpStatus.OK,grade(Map.of("q1",Integer.parseInt(score))).getStatusCode());
    var capture=org.mockito.ArgumentCaptor.forClass(Map.class);verify(storage).saveRecord(eq("submissions"),capture.capture());
    assertEquals(Integer.parseInt(score)+3,capture.getValue().get("finalScore"));assertEquals(3,submission.get("finalScore"));
  }
  @Test void storedFullScoreCannotAuthorizeMoreThanFrozenQuestionWeight(){
    view.examSnapshots.put("e1",Map.of("schemaVersion",1,"paper",Map.of("id","p1","questionIds",List.of("q1","q2")),
        "questions",List.of(Map.of("id","q1","score",5),Map.of("id","q2","score",3))));
    assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->grade(Map.of("q1",7))).getStatusCode());
    verify(storage,never()).saveRecord(anyString(),anyMap());
  }
  @Test void validExplicitCorrectionCanRepairCorruptedOldScoreWithoutChangingFrozenBounds(){
    submission.put("answerDetail",List.of(Map.of("questionId","q1","fullScore",10,"score",11),Map.of("questionId","q2","fullScore",3,"score",3)));
    assertEquals(HttpStatus.OK,grade(Map.of("q1",7)).getStatusCode());
    var capture=org.mockito.ArgumentCaptor.forClass(Map.class);verify(storage).saveRecord(eq("submissions"),capture.capture());assertEquals(10,capture.getValue().get("finalScore"));
  }
  @Test void explicitEmptyUpdateRetainsExistingCompletedRegradeSemantics(){
    submission.put("status","已完成");assertEquals(HttpStatus.OK,grade(Map.of()).getStatusCode());
    var capture=org.mockito.ArgumentCaptor.forClass(Map.class);verify(storage).saveRecord(eq("submissions"),capture.capture());assertEquals(3,capture.getValue().get("finalScore"));
  }
}
