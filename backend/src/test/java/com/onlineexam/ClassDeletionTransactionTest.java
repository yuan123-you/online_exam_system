package com.onlineexam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.service.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClassDeletionTransactionTest {
  AnnotationConfigApplicationContext context;
  JdbcTemplate jdbc; EntityCrudService crud; StoreService storage; Holder holder; SystemLogService logs;
  static class Holder { StoreService.Store view=new StoreService.Store(); }
  static class CachedViewStore extends StoreService {
    final Holder holder;
    CachedViewStore(JdbcTemplate jdbc,Holder holder) { super(jdbc,new ObjectMapper()); this.holder=holder; }
    @Override public Store readStore() { return holder.view; }
  }
  @Configuration @EnableTransactionManagement(proxyTargetClass=true)
  static class Config {
    @Bean DataSource ds(){return new DriverManagerDataSource("jdbc:h2:mem:classdel_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
    @Bean JdbcTemplate jdbc(DataSource ds){return spy(new JdbcTemplate(ds));}
    @Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
    @Bean Holder holder(){return new Holder();}
    @Bean StoreService storage(JdbcTemplate jdbc,Holder holder){return new CachedViewStore(jdbc,holder);}
    @Bean SystemLogService logs(){return mock(SystemLogService.class);}
    @Bean EntityCrudService crud(StoreService store,SystemLogService logs){return new EntityCrudService(store,mock(AuthService.class),logs);}
  }
  @BeforeEach void setUp() {
    context=new AnnotationConfigApplicationContext(Config.class); jdbc=context.getBean(JdbcTemplate.class);
    crud=context.getBean(EntityCrudService.class); storage=context.getBean(StoreService.class); holder=context.getBean(Holder.class); logs=context.getBean(SystemLogService.class);
    jdbc.execute("create table class_info(id varchar(64) primary key)"); jdbc.update("insert into class_info values('c1')");
    jdbc.execute("create table user_account(id varchar(64) primary key,class_id varchar(64),foreign key(class_id) references class_info(id) on delete set null)");
    jdbc.execute("create table exam(id varchar(64) primary key,teacher_id varchar(64),paper_id varchar(64),name varchar(100),target_class_ids_json varchar(4000),start_time timestamp,end_time timestamp,anti_cheat_limit int,published boolean,deleted int default 0)");
    jdbc.execute("create table exam_snapshot(exam_id varchar(64) primary key,content_json varchar(10000))");
    jdbc.execute("create table submission(id varchar(64),exam_id varchar(64))");
    jdbc.update("insert into exam(id,teacher_id,paper_id,name,target_class_ids_json,published) values('e1','t1','p1','Original','[\"c1\",\"c2\"]',false)");
    holder.view.users.add(Map.of("id","a1","role","admin")); holder.view.classes.add(Map.of("id","c1"));
    holder.view.exams.add(new LinkedHashMap<>(Map.of("id","e1","teacherId","t1","paperId","p1","name","Original","targetClassIds",List.of("c1","c2"),"published",false)));
  }
  @AfterEach void close(){jdbc.execute("drop all objects");context.close();}
  org.springframework.http.ResponseEntity<?> remove(){return crud.deleteEntity("a1","classes","c1");}
  String roster(){return jdbc.queryForObject("select target_class_ids_json from exam where id='e1'",String.class);}
  long classes(){return jdbc.queryForObject("select count(*) from class_info where id='c1'",Long.class);}
  @Test void currentReferencesBlockDeletionEvenWhenCachedUserListHasNoStudents() {
    jdbc.update("insert into user_account values('u1','c1')");
    var before=new LinkedHashMap<>(holder.view.exams.getFirst());
    assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,this::remove).getStatusCode().value());
    assertEquals(1,classes()); assertEquals("c1",jdbc.queryForObject("select class_id from user_account where id='u1'",String.class));
    assertEquals("[\"c1\",\"c2\"]",roster()); assertEquals(before,holder.view.exams.getFirst());
  }
  @Test void databaseDeletionFailureRollsBackAllRosterChangesAndNeverMutatesCachedRows() {
    jdbc.execute("create table class_hold(id int primary key,class_id varchar(64) references class_info(id))"); jdbc.update("insert into class_hold values(1,'c1')");
    var before=new LinkedHashMap<>(holder.view.exams.getFirst());
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,this::remove);
    assertEquals(1,classes()); assertEquals("[\"c1\",\"c2\"]",roster()); assertEquals(before,holder.view.exams.getFirst());
    verifyNoInteractions(logs);
  }
  @Test void removesEveryOccurrenceFromCurrentRosterAndPreservesConcurrentMetadata() {
    jdbc.update("update exam set teacher_id='t2',name='Concurrent',target_class_ids_json='[\"c1\",\"c2\",\"c1\",\"c3\"]' where id='e1'");
    assertEquals(200,remove().getStatusCode().value()); assertEquals(0,classes());
    assertEquals("[\"c2\",\"c3\"]",roster()); assertEquals("Concurrent",jdbc.queryForObject("select name from exam where id='e1'",String.class));
    assertEquals("t2",jdbc.queryForObject("select teacher_id from exam where id='e1'",String.class));
    assertEquals(List.of("c1","c2"),holder.view.exams.getFirst().get("targetClassIds"));
  }
  @Test void mandatoryAuditFailureRollsBackPhysicalDeleteAndRoster() {
    doThrow(new IllegalStateException("audit failure")).when(logs).log(anyMap(),anyString(),anyString());
    assertThrows(IllegalStateException.class,this::remove);
    assertEquals(1,classes()); assertEquals("[\"c1\",\"c2\"]",roster());
  }
  @Test void actualAuditRowAndDeletionRollbackTogetherOnLaterAuditFailure() {
    jdbc.execute("create table system_log(id varchar(64) primary key,actor_id varchar(64),action varchar(100),detail varchar(1000),time timestamp)");
    doAnswer(inv->{
      new SystemLogService(storage).log(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2));
      assertEquals(1,jdbc.queryForObject("select count(*) from system_log",Integer.class));
      throw new IllegalStateException("failure after audit SQL");
    }).when(logs).log(anyMap(),anyString(),anyString());
    assertThrows(IllegalStateException.class,this::remove);
    assertEquals(1,classes()); assertEquals("[\"c1\",\"c2\"]",roster());
    assertEquals(0,jdbc.queryForObject("select count(*) from system_log",Integer.class));
  }
  @Test void concurrentNewBindingCannotBecomeNullThroughSetNullDeletion() throws Exception {
    var checked=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
    var started=new java.util.concurrent.CountDownLatch(1); var userFinished=new java.util.concurrent.CountDownLatch(1);
    doAnswer(inv->{
      Object rows=inv.callRealMethod(); checked.countDown();
      assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS)); return rows;
    }).when(jdbc).queryForList("select id from user_account where class_id=? for update","c1");
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var deleting=pool.submit(()->{ try{return remove().getStatusCode().value();}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();} });
      assertTrue(checked.await(3,java.util.concurrent.TimeUnit.SECONDS));
      var adding=pool.submit(()->{started.countDown();try{jdbc.update("insert into user_account values('late','c1')");return true;}
        catch(org.springframework.dao.DataIntegrityViolationException rejected){return false;}finally{userFinished.countDown();}});
      assertTrue(started.await(3,java.util.concurrent.TimeUnit.SECONDS));
      // Either FK checking blocks the insertion, or it commits first. Both must preserve the final binding invariant.
      userFinished.await(300,java.util.concurrent.TimeUnit.MILLISECONDS); release.countDown();
      int status=deleting.get(6,java.util.concurrent.TimeUnit.SECONDS); boolean added=adding.get(6,java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(status==200 || status==409);
      assertEquals(0,jdbc.queryForObject("select count(*) from user_account where class_id is null",Integer.class));
      if(added){assertEquals(1,classes());assertEquals("c1",jdbc.queryForObject("select class_id from user_account where id='late'",String.class));}
    } finally {release.countDown();pool.shutdownNow();}
  }
  @Test void zeroAffectedDeleteDoesNotReportSuccessOrCommitRosterRemoval() {
    doReturn(0).when(jdbc).update("delete from class_info where id=? and not exists (select 1 from user_account where class_id=?)","c1","c1");
    assertEquals(409,assertThrows(org.springframework.web.server.ResponseStatusException.class,this::remove).getStatusCode().value());
    assertEquals(1,classes()); assertEquals("[\"c1\",\"c2\"]",roster()); verifyNoInteractions(logs);
  }
  @Test void rosterOnlyCleanupDoesNotGrantOrBackfillMissingLegacyPublishedVersion() {
    jdbc.update("update exam set published=true where id='e1'"); holder.view.exams.getFirst().put("published",true);
    assertEquals(200,remove().getStatusCode().value()); assertEquals("[\"c2\"]",roster());
    assertEquals(true,jdbc.queryForObject("select published from exam where id='e1'",Boolean.class));
    assertEquals(0,jdbc.queryForObject("select count(*) from exam_snapshot",Integer.class));
  }
  @Test void malformedRosterRollsBackRatherThanDiscardingUnknownReferences() {
    jdbc.update("update exam set target_class_ids_json='{}' where id='e1'");
    assertThrows(org.springframework.web.server.ResponseStatusException.class,this::remove);
    assertEquals(1,classes()); assertEquals("{}",roster());
  }
}
