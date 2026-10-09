package com.onlineexam.service;
import com.onlineexam.StoreService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ResourceReferenceIntegrityTest {
  StoreService storage; StoreService.Store view; EntityCrudService service;
  @BeforeEach void setup(){
    storage=mock(StoreService.class);view=new StoreService.Store();when(storage.readStore()).thenReturn(view);
    view.users.add(Map.of("id","t1","role","teacher"));view.classes.add(Map.of("id","c1"));
    view.questions.add(new LinkedHashMap<>(Map.of("id","q1","teacherId","t1","score",5)));
    view.questions.add(Map.of("id","foreign","teacherId","t2","score",5));
    view.papers.add(Map.of("id","p1","teacherId","t1","questionIds",List.of("q1")));
    view.papers.add(Map.of("id","foreign","teacherId","t2","questionIds",List.of("foreign")));
    service=new EntityCrudService(storage,mock(AuthService.class),mock(SystemLogService.class));
  }
  @ParameterizedTest @ValueSource(strings={"foreign","missing"})
  void paperCannotCopyUnavailableQuestionThroughReference(String id){
    var response=service.createEntity("t1",Map.of("entity","papers","record",Map.of("name","New","durationMinutes",30,"questionIds",List.of(id))));
    assertEquals(HttpStatus.BAD_REQUEST,response.getStatusCode()); verify(storage,never()).createRecord(anyString(),anyMap());
  }
  @ParameterizedTest @ValueSource(strings={"foreign","missing"})
  void examCannotIndirectlyExposeUnavailablePaper(String id){
    var response=service.createEntity("t1",Map.of("entity","exams","record",Map.of("name","New","paperId",id,"targetClassIds",List.of("c1"),"startTime","2026-10-10T00:00:00Z","endTime","2026-10-10T01:00:00Z")));
    assertEquals(HttpStatus.BAD_REQUEST,response.getStatusCode()); verify(storage,never()).createRecord(anyString(),anyMap());
  }
  @Test void duplicatedQuestionIdCannotCreatePaperWithDoubleCountedScore(){
    var response=service.createEntity("t1",Map.of("entity","papers","record",Map.of("name","New","durationMinutes",30,"questionIds",List.of("q1","q1"))));
    assertEquals(HttpStatus.BAD_REQUEST,response.getStatusCode()); verify(storage,never()).createRecord(anyString(),anyMap());
  }
  @Test void derivedPaperTotalCannotOverflowIntoNegativeScore(){
    view.questions.getFirst().put("score",Integer.MAX_VALUE);view.questions.add(Map.of("id","q2","teacherId","t1","score",1));
    var response=service.createEntity("t1",Map.of("entity","papers","record",Map.of("name","New","durationMinutes",30,"questionIds",List.of("q1","q2"))));
    assertEquals(HttpStatus.BAD_REQUEST,response.getStatusCode()); verify(storage,never()).createRecord(anyString(),anyMap());
  }
  @ParameterizedTest @ValueSource(doubles={2.5,4294967297.0})
  void questionWeightCannotBeTruncatedOrWrapped(double score){
    var response=service.createEntity("t1",Map.of("entity","questions","record",Map.of("title","New","subject","Math","type","single","score",score)));
    assertEquals(HttpStatus.BAD_REQUEST,response.getStatusCode()); verify(storage,never()).createRecord(anyString(),anyMap());
  }
  @Test void ownPaperReferenceRemainsSupported(){
    assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","papers","record",Map.of("name","New","durationMinutes",30,"questionIds",List.of("q1")))).getStatusCode());
  }
  @Test void frozenVersionStillSupportsMetadataUpdateAfterLivePaperIsRemoved(){
    view.exams.add(Map.of("id","e1","teacherId","t1","paperId","p1","name","Exam","targetClassIds",List.of("c1"),"published",true,"startTime","2026-10-10T00:00:00Z","endTime","2026-10-10T01:00:00Z"));
    view.papers.clear();view.examSnapshots.put("e1",Map.of("schemaVersion",1,"paper",Map.of("id","p1","teacherId","t1","questionIds",List.of("q1")),"questions",List.of(Map.of("id","q1"))));
    assertEquals(HttpStatus.OK,service.updateEntity("t1","exams",Map.of("id","e1","name","Edited")).getStatusCode());
  }
}
