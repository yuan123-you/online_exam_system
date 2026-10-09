package com.onlineexam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class ResourceInsertOnlyTest {
  SingleConnectionDataSource ds; JdbcTemplate jdbc; StoreService store;
  @BeforeEach void setup(){
    ds=new SingleConnectionDataSource("jdbc:h2:mem:insert_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","",true);
    jdbc=new JdbcTemplate(ds); store=new StoreService(jdbc,new ObjectMapper());
    jdbc.execute("create table department(id varchar(64) primary key,name varchar(100))");
    jdbc.execute("create table class_info(id varchar(64) primary key,name varchar(100),major varchar(100),department_id varchar(64))");
    jdbc.execute("create table user_account(id varchar(64) primary key,role varchar(20),username varchar(50) unique,password varchar(100),name varchar(100),department_id varchar(64),class_id varchar(64),major varchar(100))");
    jdbc.execute("create table question(id varchar(64) primary key,teacher_id varchar(64),subject varchar(100),knowledge_point varchar(100),difficulty varchar(20),type varchar(20),title varchar(100),options_json varchar(2000),answer_json varchar(2000),score int,source_tag varchar(100),deleted int,explanation varchar(2000))");
    jdbc.execute("create table paper(id varchar(64) primary key,teacher_id varchar(64),name varchar(100),duration_minutes int,total_score int,pass_score int,question_ids_json varchar(2000),paper_type varchar(50),source_tag varchar(100),deleted int)");
    jdbc.execute("create table exam(id varchar(64) primary key,teacher_id varchar(64),paper_id varchar(64),name varchar(100),target_class_ids_json varchar(2000),start_time timestamp,end_time timestamp,anti_cheat_limit int,published boolean,deleted int)");
    jdbc.execute("create table exam_snapshot(exam_id varchar(64) primary key,content_json varchar(10000))"); jdbc.execute("create table submission(id varchar(64),exam_id varchar(64))");
  }
  @AfterEach void close(){ds.destroy();}
  Map<String,Object> record(String id,String name){
    var r=new LinkedHashMap<String,Object>(); r.put("id",id); r.put("name",name);r.put("title",name);r.put("role","student");r.put("username",id);
    r.put("password","stored-hash"); r.put("teacherId","t1");r.put("paperId","p1");r.put("subject","Math");r.put("type","single");r.put("score",5);
    r.put("questionIds",List.of("q1"));r.put("targetClassIds",List.of("c1"));r.put("published",false);return r;
  }
  void create(String entity,Map<String,Object> row){
    try {StoreService.class.getMethod("createRecord",String.class,Map.class).invoke(store,entity,row);}
    catch(NoSuchMethodException old){store.saveRecord(entity,row);}
    catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new RuntimeException(e.getCause());}
    catch(ReflectiveOperationException e){throw new AssertionError(e);}
  }
  String table(String kind){return switch(kind){case "departments"->"department";case "classes"->"class_info";case "users"->"user_account";case "questions"->"question";case "papers"->"paper";case "exams"->"exam";default->throw new IllegalArgumentException();};}
  @ParameterizedTest @ValueSource(strings={"departments","classes","users","questions","papers","exams"})
  void createCollisionCannotOverwriteAnyResource(String kind){
    store.saveRecord(kind,record("r1","Original"));
    var error=assertThrows(ResponseStatusException.class,()->create(kind,record("r1","Overwritten")));assertEquals(409,error.getStatusCode().value());
    String field=kind.equals("questions")?"title":"name";
    assertEquals("Original",jdbc.queryForObject("select "+field+" from "+table(kind)+" where id='r1'",String.class));
  }
  void deleteOwned(String entity,String owner){
    try {StoreService.class.getMethod("deleteOwnedResource",String.class,String.class,String.class).invoke(store,entity,"r1",owner);}
    catch(NoSuchMethodException old){store.deleteRecord(entity,"r1");}
    catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new RuntimeException(e.getCause());}
    catch(ReflectiveOperationException e){throw new AssertionError(e);}
  }
  @ParameterizedTest @ValueSource(strings={"questions","papers","exams"})
  void authoritativeOwnerPredicatePreventsStaleOrForeignDeletion(String kind){
    store.saveRecord(kind,record("r1","Original"));
    assertEquals(409,assertThrows(ResponseStatusException.class,()->deleteOwned(kind,"t2")).getStatusCode().value());
    assertEquals(0,jdbc.queryForObject("select deleted from "+table(kind)+" where id='r1'",Integer.class));
    assertDoesNotThrow(()->deleteOwned(kind,"t1"));
    assertEquals(1,jdbc.queryForObject("select deleted from "+table(kind)+" where id='r1'",Integer.class));
    assertEquals(409,assertThrows(ResponseStatusException.class,()->deleteOwned(kind,"t1")).getStatusCode().value());
  }
  @Test void lockedPublicationCannotCaptureAnotherTeachersPrivatePaper(){
    var question=record("q1","Private");question.put("teacherId","t2");store.saveRecord("questions",question);
    var paper=record("p1","Private");paper.put("teacherId","t2");paper.put("totalScore",5);store.saveRecord("papers",paper);
    var exam=record("e1","Public");exam.put("published",true);
    assertThrows(ResponseStatusException.class,()->create("exams",exam));
    assertEquals(0,jdbc.queryForObject("select count(*) from exam",Integer.class));
    assertEquals(0,jdbc.queryForObject("select count(*) from exam_snapshot",Integer.class));
  }
  @Test void newExamCannotAdoptOrphanedHistoricalSnapshotOnAnIdCollision(){
    jdbc.update("insert into exam_snapshot values('r1',?)","{\"schemaVersion\":1,\"paper\":{\"id\":\"p1\",\"questionIds\":[\"q1\"],\"teacherId\":\"t2\"},\"questions\":[{\"id\":\"q1\",\"answer\":[\"private\"]}]}");
    assertEquals(409,assertThrows(ResponseStatusException.class,()->create("exams",record("r1","New"))).getStatusCode().value());
    assertEquals(0,jdbc.queryForObject("select count(*) from exam",Integer.class));
    assertTrue(jdbc.queryForObject("select content_json from exam_snapshot where exam_id='r1'",String.class).contains("private"));
  }
  @ParameterizedTest @ValueSource(strings={"departments","classes","users","questions","papers","exams"})
  void distinctCreationStillPersistsBothResources(String kind){
    create(kind,record("r1","First"));create(kind,record("r2","Second"));
    assertEquals(2,jdbc.queryForObject("select count(*) from "+table(kind),Integer.class));
  }
}
