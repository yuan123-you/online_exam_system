package com.onlineexam.service;
import com.onlineexam.StoreService;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceCreationIntegrityTest {
  StoreService.Store view;
  StoreService storage;
  EntityCrudService service;
  List<Map<String,Object>> written;
  @BeforeEach void setUp() {
    view=new StoreService.Store(); written=new ArrayList<>();
    view.users.add(Map.of("id","a1","role","admin")); view.users.add(Map.of("id","t1","role","teacher"));
    view.classes.add(Map.of("id","c1")); view.departments.add(Map.of("id","d1"));
    view.questions.add(Map.of("id","q1","teacherId","t1","score",5));
    view.questions.add(Map.of("id","victim","teacherId","t2","score",5,"title","Untouched"));
    view.papers.add(Map.of("id","p1","teacherId","t1","questionIds",List.of("q1"),"totalScore",5));
    storage=mock(StoreService.class,inv->{
      if(inv.getMethod().getName().equals("readStore")) return view;
      if(Set.of("saveRecord","createRecord").contains(inv.getMethod().getName())) written.add(new LinkedHashMap<>(inv.getArgument(1)));
      return org.mockito.Answers.RETURNS_DEFAULTS.answer(inv);
    });
    var auth=mock(AuthService.class); when(auth.hashPassword(org.mockito.ArgumentMatchers.anyString())).thenReturn("test-hash");
    service=new EntityCrudService(storage,auth,mock(SystemLogService.class));
  }
  static Stream<Arguments> kinds() {
    return Stream.of(Arguments.of("users","a1",Map.of("username","newstudent","password","pass1234","name","New","role","student","classId","c1")),
        Arguments.of("classes","a1",Map.of("name","New","major","CS","departmentId","d1")),
        Arguments.of("departments","a1",Map.of("name","New")),
        Arguments.of("questions","t1",Map.of("title","New","subject","Math","type","single","score",5,"teacherId","t2")),
        Arguments.of("papers","t1",Map.of("name","New","durationMinutes",30,"questionIds",List.of("q1"))),
        Arguments.of("exams","t1",Map.of("name","New","paperId","p1","targetClassIds",List.of("c1"),"startTime","2026-10-10T00:00:00Z","endTime","2026-10-10T01:00:00Z")));
  }
  @ParameterizedTest @MethodSource("kinds")
  void suppliedExistingIdNeverBecomesCreateWriteIdentity(String kind,String caller,Map<String,Object> fields) {
    var input=new LinkedHashMap<>(fields); input.put("id","victim");
    var response=service.createEntity(caller,Map.of("entity",kind,"record",input));
    assertEquals(HttpStatus.OK,response.getStatusCode()); assertEquals(1,written.size());
    String id=String.valueOf(written.getFirst().get("id")); assertFalse(id.isBlank()); assertNotEquals("victim",id);
    assertEquals("victim",input.get("id"));
    assertEquals("Untouched",view.questions.get(1).get("title"));
    if("t1".equals(caller)) assertEquals("t1",written.getFirst().get("teacherId"));
  }
  @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","untrusted|session-id"})
  void invalidClientIdentityCannotCreateUnusableOrCallerOwnedIdentifier(String clientId) {
    var input=new LinkedHashMap<String,Object>(Map.of("title","New","subject","Math","type","single","score",5)); input.put("id",clientId);
    assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","questions","record",input)).getStatusCode());
    String id=String.valueOf(written.getFirst().get("id")); assertFalse(id.isBlank()); assertFalse(id.contains("|"));
    assertNotEquals(clientId,id);
  }
  @Test void repeatedCreatesWithSameSuppliedIdProduceSeparateResources() {
    var record=Map.<String,Object>of("id","victim","title","New","subject","Math","type","single","score",5);
    assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","questions","record",record)).getStatusCode());
    assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","questions","record",record)).getStatusCode());
    assertNotEquals(written.get(0).get("id"),written.get(1).get("id"));
  }
  @Test void blockedClassDeletionDoesNotWriteOrMutateExamReferences() {
    view.users.add(Map.of("id","u1","role","student","classId","c1"));
    var exam=new LinkedHashMap<String,Object>(Map.of("id","e1","targetClassIds",List.of("c1","c2")));
    view.exams.add(exam); var before=new LinkedHashMap<>(exam);
    assertEquals(HttpStatus.BAD_REQUEST,service.deleteEntity("a1","classes","c1").getStatusCode());
    assertEquals(before,exam); assertTrue(written.isEmpty());
    verify(storage,never()).deleteRecord(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
  }
}
