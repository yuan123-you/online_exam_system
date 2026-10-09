package com.onlineexam.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.StoreService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ArchivedExamHistoryTest {
  StoreService storage;StoreService.Store view;SubmissionService receipts;BootstrapService bootstrap;
  @BeforeEach void setup(){
    storage=mock(StoreService.class);view=new StoreService.Store();when(storage.readStore()).thenReturn(view);
    view.users.add(Map.of("id","u1","role","student","classId","c1"));view.users.add(Map.of("id","t1","role","teacher"));view.users.add(Map.of("id","t2","role","teacher"));
    view.submissions.add(new LinkedHashMap<>(Map.of("id","s1","examId","e1","studentId","u1","status","已完成","finalScore",3)));
    view.examSnapshots.put("e1",Map.of("schemaVersion",1,"paper",Map.of("id","p1","teacherId","t1","name","Frozen Paper","questionIds",List.of("q1"),"totalScore",10,"passScore",6,"durationMinutes",30),"questions",List.of(Map.of("id","q1","answer",List.of("private")))));
    archived(Map.of("id","e1","teacherId","t1","paperId","p1","name","Original Exam","targetClassIds",List.of("c1"),"published",true,"deleted",true));
    var exams=new ExamService(storage,mock(SystemLogService.class)); receipts=new SubmissionService(storage,exams,mock(SystemLogService.class),mock(WrongBookService.class));
    bootstrap=new BootstrapService(storage,exams,receipts,mock(WrongBookService.class));
  }
  @SuppressWarnings("unchecked") void archived(Map<String,Object> exam){
    try {((Map<String,Map<String,Object>>) StoreService.Store.class.getField("archivedExams").get(view)).put(String.valueOf(exam.get("id")),new LinkedHashMap<>(exam));}
    catch(NoSuchFieldException legacy){/* Before implementation, only the unsafe active lookup exists. */}
    catch(ReflectiveOperationException e){throw new AssertionError(e);}
  }
  @Test void knownArchivedVersionRetainsReceiptAndOriginalPassThreshold(){
    var result=receipts.buildSubmissionReview(view,view.submissions.getFirst());
    assertEquals("Original Exam",result.get("examName"));assertEquals("Frozen Paper",result.get("paperName"));
    assertEquals(3,result.get("finalScore"));assertEquals(10,result.get("totalScore"));assertEquals(6,result.get("passScore"));
    assertEquals("未及格",result.get("passStatus"));assertEquals("FROZEN",result.get("contentVersionStatus"));
  }
  @Test void physicallyMissingMetadataCannotDeclarePassEvenWithSavedScore(){
    view.submissions.getFirst().put("examId","missing");
    var result=receipts.buildSubmissionReview(view,view.submissions.getFirst());
    assertEquals(3,result.get("finalScore"));assertEquals("MISSING",result.get("contentVersionStatus"));
    assertEquals("版本待恢复",result.get("passStatus"));assertNull(result.get("scoreRate"));
  }
  @Test void missingArchiveVersionNeverUsesEditedLivePaperOrInventsThreshold(){
    view.examSnapshots.clear();view.papers.add(Map.of("id","p1","totalScore",100,"passScore",1,"name","Edited"));
    var result=receipts.buildSubmissionReview(view,view.submissions.getFirst());
    assertEquals("版本待恢复",result.get("passStatus"));assertEquals(3,result.get("finalScore"));assertEquals("MISSING",result.get("contentVersionStatus"));
  }
  @Test void scoreTrendUsesKnownArchivedFrozenMetadata(){
    var response=new AnalysisService(storage).scoreTrend("u1",view);assertEquals(HttpStatus.OK,response.getStatusCode());
    var entry=(Map<?,?>)((List<?>)((Map<?,?>)response.getBody()).get("trend")).getFirst();
    assertEquals("Original Exam",entry.get("examName"));assertEquals(10,entry.get("totalScore"));assertEquals(6,entry.get("passScore"));
  }
  @Test void teacherCanReadOwnArchivedSubmissionWithoutReturningActiveExam(){
    var result=bootstrap.buildBootstrap(view,view.users.get(1));
    assertEquals(1,((List<?>)result.get("submissions")).size());assertTrue(((List<?>)result.get("exams")).isEmpty());
    assertFalse(result.containsKey("examSnapshots"));assertFalse(result.containsKey("archivedExams"));
  }
  @Test void unrelatedTeacherCannotReadArchivedSubmission(){
    var result=bootstrap.buildBootstrap(view,view.users.get(2));assertTrue(((List<?>)result.get("submissions")).isEmpty());
    assertFalse(new DataIsolationService(storage).canAccessSubmission("t2","s1"));
  }
  @Test void ownerHistoryAuthorizationDoesNotReenableActiveExamAccess(){
    var access=new DataIsolationService(storage);assertTrue(access.canAccessSubmission("t1","s1"));
    assertTrue(access.canAccessSubmission("u1","s1"));assertFalse(access.canAccessExam("t1","e1"));
    assertFalse(access.canManageExam("t1","e1"));assertTrue(view.exams.isEmpty());
  }
  @Test void realStoreLoaderSeparatesDeletedExamHeadersFromActiveLists() throws Exception {
    var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
    when(jdbc.queryForList(startsWith("select e.* from exam e where e.deleted=1"))).thenReturn(List.of(Map.of("id","e1","teacher_id","t1","paper_id","p1","name","Original Exam","published",1,"deleted",1,"target_class_ids_json","[\"c1\"]")));
    when(jdbc.queryForList("select * from submission order by updated_at desc,id")).thenReturn(List.of(Map.of("id","s1","exam_id","e1","student_id","u1","status","已完成","final_score",3)));
    when(jdbc.queryForList("select exam_id,content_json from exam_snapshot")).thenReturn(List.of(Map.of("exam_id","e1","content_json",new ObjectMapper().writeValueAsString(view.examSnapshots.get("e1")))));
    var loaded=new StoreService(jdbc,new ObjectMapper()).readStore();
    var receipt=receipts.buildSubmissionReview(loaded,loaded.submissions.getFirst());
    assertTrue(loaded.exams.isEmpty());assertTrue(loaded.papers.isEmpty());assertEquals("Original Exam",receipt.get("examName"));assertEquals(6,receipt.get("passScore"));
    assertFalse(new ObjectMapper().writeValueAsString(loaded).contains("archivedExams"));
  }
  @Test void metadataReadFailureDoesNotMasqueradeAsSuccessfulEmptyBootstrap(){
    var failing=mock(StoreService.class);when(failing.readStore()).thenThrow(new IllegalStateException("db unavailable"));
    var response=new BootstrapService(failing,mock(ExamService.class),mock(SubmissionService.class),mock(WrongBookService.class)).bootstrap("u1");
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE,response.getStatusCode());
  }
  @Test void unknownPassThresholdIsNotAZeroDefaultInTheApi(){
    view.examSnapshots.clear();var result=receipts.buildSubmissionReview(view,view.submissions.getFirst());
    assertNull(result.get("passScore"));assertEquals(false,result.get("passThresholdKnown"));
    assertEquals("版本待恢复",result.get("passStatus"));assertEquals(3,result.get("finalScore"));
  }
  @Test void bootstrapPreservesVersionDecodeConflictBeforePayloadConstruction(){
    var failing=mock(StoreService.class);
    when(failing.readStore()).thenThrow(new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"版本需要恢复"));
    var response=new BootstrapService(failing,mock(ExamService.class),mock(SubmissionService.class),mock(WrongBookService.class)).bootstrap("u1");
    assertEquals(HttpStatus.CONFLICT,response.getStatusCode());assertEquals("版本需要恢复",((Map<?,?>)response.getBody()).get("message"));
  }
  @Test void rawPrivateVersionsAreNotSerializableFromStore() throws Exception {
    String serialized=new ObjectMapper().writeValueAsString(view);
    assertFalse(serialized.contains("examSnapshots"));assertFalse(serialized.contains("private"));
    assertFalse(serialized.contains("archivedExams"));
  }
  @Test void teacherStatsIncludeOwnedHistoryButActiveExamCountStaysZero(){
    var body=(Map<?,?>)bootstrap.stats("t1").getBody();assertEquals(1,body.get("totalSubmissions"));assertEquals(1L,body.get("finished"));assertEquals(0,body.get("totalExams"));
  }
}
