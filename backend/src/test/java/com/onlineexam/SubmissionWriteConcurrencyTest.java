package com.onlineexam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.common.JsonHelper;
import com.onlineexam.repository.SubmissionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionWriteConcurrencyTest {
  AnnotationConfigApplicationContext context;
  JdbcTemplate jdbc;
  StoreService storage;
  SubmissionRepository repository;
  TransactionTemplate transaction;

  @Configuration @EnableTransactionManagement(proxyTargetClass=true)
  static class Config {
    @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:submission_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", ""); }
    @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
    @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    @Bean StoreService storage(JdbcTemplate jdbc) { return new StoreService(jdbc,new ObjectMapper()); }
    @Bean SubmissionRepository repository(JdbcTemplate jdbc) { return new SubmissionRepository(jdbc,new JsonHelper(new ObjectMapper())); }
  }
  @BeforeEach void setUp() {
    context=new AnnotationConfigApplicationContext(Config.class);
    jdbc=context.getBean(JdbcTemplate.class); storage=context.getBean(StoreService.class); repository=context.getBean(SubmissionRepository.class);
    transaction=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    jdbc.execute("""
      create table submission(id varchar(64) primary key, exam_id varchar(64) not null, student_id varchar(64) not null,
      student_name varchar(50), answers_json varchar(5000), answer_detail_json varchar(5000),switch_count int,suspicious boolean,
      suspicious_reasons_json varchar(2000),auto_score int,final_score int,status varchar(20),started_at timestamp,deadline_at timestamp,
      submitted_at timestamp,updated_at timestamp,manual_extended_minutes int,graded_by varchar(50),question_order_json varchar(2000),
      option_order_json varchar(2000),revision bigint not null default 0,unique(exam_id,student_id))
      """);
  }
  @AfterEach void tearDown() { jdbc.execute("drop all objects"); context.close(); }
  Map<String,Object> candidate(String id) {
    var r=new LinkedHashMap<String,Object>(); r.put("id",id); r.put("examId","e1"); r.put("studentId","u1"); r.put("studentName","Student");
    r.put("status","进行中"); r.put("answers",List.of(Map.of("questionId","q1","answer",List.of("A"))));
    r.put("questionOrder",List.of("q1")); r.put("optionOrder",Map.of("q1",List.of(0,1)));
    r.put("startedAt","2026-10-08T00:00:00Z"); r.put("deadlineAt","2026-10-08T01:00:00Z"); r.put("updatedAt","2026-10-08T00:00:00Z");
    return r;
  }
  void save(boolean viaStore,Map<String,Object> record) {
    if(viaStore) storage.saveRecord("submissions",record); else repository.save(record);
  }
  Map<String,Object> current() { return repository.findAll().getFirst(); }
  void assertConflict(Runnable work) { assertEquals(409,assertThrows(ResponseStatusException.class,work::run).getStatusCode().value()); }

  @ParameterizedTest @ValueSource(booleans={true,false})
  void staleAnswerSaveCannotReopenSubmittedOrGradedSession(boolean viaStore) {
    save(viaStore,candidate("s1"));
    var old=candidate("s1"); old.put("revision",0L);
    var submitted=candidate("s1"); submitted.put("revision",0L); submitted.put("status","已完成"); submitted.put("finalScore",8);
    save(viaStore,submitted);
    old.put("answers",List.of(Map.of("questionId","q1","answer",List.of("B"))));
    assertConflict(()->save(viaStore,old));
    assertEquals("已完成",current().get("status")); assertEquals(8,current().get("finalScore")); assertEquals(1L,current().get("revision"));
  }
  @ParameterizedTest @ValueSource(booleans={true,false})
  void duplicateNewIdCannotOverwriteExistingStudentExamSession(boolean viaStore) {
    save(viaStore,candidate("s1"));
    var duplicate=candidate("s2"); duplicate.put("status","已完成"); duplicate.put("finalScore",9);
    assertConflict(()->save(viaStore,duplicate));
    assertEquals(1,repository.findAll().size()); assertEquals("s1",current().get("id")); assertEquals("进行中",current().get("status"));
  }
  @ParameterizedTest @ValueSource(booleans={true,false})
  void missingRevisionCannotOverwriteExistingId(boolean viaStore) {
    save(viaStore,candidate("s1")); assertConflict(()->save(viaStore,candidate("s1")));
    assertEquals("进行中",current().get("status"));
  }
  @ParameterizedTest @ValueSource(booleans={true,false})
  void wrongStudentIdentityCannotAlterRecordEvenWithMatchingRevision(boolean viaStore) {
    save(viaStore,candidate("s1")); var wrong=candidate("s1"); wrong.put("revision",0L); wrong.put("studentId","u2"); wrong.put("finalScore",10);
    assertConflict(()->save(viaStore,wrong)); assertEquals("u1",current().get("studentId")); assertEquals(0,current().get("finalScore"));
  }
  @ParameterizedTest @ValueSource(booleans={true,false})
  void completedCannotReturnToRunningEvenWithFreshRevision(boolean viaStore) {
    var created=candidate("s1"); created.put("status","已完成"); save(viaStore,created);
    var reopened=candidate("s1"); reopened.put("revision",0L);
    assertConflict(()->save(viaStore,reopened)); assertEquals("已完成",current().get("status"));
  }
  @ParameterizedTest @ValueSource(booleans={true,false})
  void freshManualRegradeIsAllowedAndIncrementsRevision(boolean viaStore) {
    var created=candidate("s1"); created.put("status","已完成"); save(viaStore,created);
    var graded=candidate("s1"); graded.put("status","已完成"); graded.put("revision",0L); graded.put("finalScore",7);
    save(viaStore,graded);
    var regraded=new LinkedHashMap<>(current()); assertEquals(1L,regraded.get("revision")); regraded.put("finalScore",9); save(viaStore,regraded);
    assertEquals(9,current().get("finalScore")); assertEquals(2L,current().get("revision"));
  }
  @ParameterizedTest @ValueSource(booleans={true,false})
  void simultaneousWritesHaveExactlyOneWinner(boolean viaStore) throws Exception {
    save(viaStore,candidate("s1")); var start=new CyclicBarrier(2); var won=new AtomicInteger(); var conflicted=new AtomicInteger();
    ExecutorService pool=Executors.newFixedThreadPool(2);
    try {
      var attempts=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{
        var r=candidate("s1"); r.put("revision",0L); r.put("status","已完成"); r.put("finalScore",i+5);
        try { start.await(3,TimeUnit.SECONDS); save(viaStore,r); won.incrementAndGet(); }
        catch(ResponseStatusException e) { assertEquals(409,e.getStatusCode().value()); conflicted.incrementAndGet(); }
        catch(Exception e) { throw new RuntimeException(e); }
      })).toList();
      for(Future<?> attempt:attempts) attempt.get(8,TimeUnit.SECONDS);
      assertEquals(1,won.get()); assertEquals(1,conflicted.get()); assertEquals(1L,current().get("revision"));
    } finally { pool.shutdownNow(); }
  }
  @Test void legacyCompletedSpellingCanBeRegradedWithFreshServerRevision() {
    var r=candidate("s1"); r.put("status","已完成"); repository.save(r);
    jdbc.update("update submission set status='完成' where id='s1'");
    var loaded=current(); assertEquals("已完成",loaded.get("status")); loaded.put("finalScore",9);
    assertDoesNotThrow(()->repository.save(loaded));
    assertEquals("已完成",current().get("status")); assertEquals(9,current().get("finalScore"));
    assertEquals(1L,current().get("revision"));
  }
  @Test void loadStartedBeforeCommitCannotRepublishStaleCacheAfterInvalidation() throws Exception {
    repository.save(candidate("s1"));
    var reader=spy(jdbc); emptyOtherReadSections(reader);
    CountDownLatch loadedOld=new CountDownLatch(1), release=new CountDownLatch(1);
    doAnswer(inv->{ loadedOld.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return List.of(); })
        .when(reader).queryForList("select * from notification order by created_at desc limit 200");
    var shared=new StoreService(reader,new ObjectMapper()); var pool=Executors.newSingleThreadExecutor();
    var future=new java.util.concurrent.atomic.AtomicReference<Future<StoreService.Store>>();
    try {
      transaction.executeWithoutResult(status->{
        var r=candidate("s1"); r.put("revision",0L); r.put("status","已完成"); shared.saveRecord("submissions",r);
        future.set(pool.submit(shared::readStore));
        try { assertTrue(loadedOld.await(3,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new RuntimeException(e); }
      });
      release.countDown();
      assertEquals("进行中",future.get().get(4,TimeUnit.SECONDS).submissions.getFirst().get("status"));
      assertNull(ReflectionTestUtils.getField(shared,"cachedStore"));
      doReturn(List.of()).when(reader).queryForList("select * from notification order by created_at desc limit 200");
      assertEquals("已完成",shared.readStore().submissions.getFirst().get("status"));
    } finally { release.countDown(); pool.shutdownNow(); }
  }
  @Test void malformedRevisionCannotBeCoercedIntoFreshRevision() {
    repository.save(candidate("s1"));
    for(Object bad:java.util.Arrays.asList(null,-1,0.5,"0",Long.MAX_VALUE)) {
      var r=candidate("s1"); r.put("revision",bad);
      assertThrows(ResponseStatusException.class,()->repository.save(r));
    }
    assertEquals(0L,current().get("revision"));
  }
  @Test void outerRollbackRestoresVersionAndGrade() {
    storage.saveRecord("submissions",candidate("s1"));
    transaction.executeWithoutResult(status->{ var r=candidate("s1"); r.put("revision",0L); r.put("status","已完成"); r.put("finalScore",10);
      storage.saveRecord("submissions",r); status.setRollbackOnly(); });
    assertEquals("进行中",current().get("status")); assertEquals(0L,current().get("revision"));
  }
  @Test void persistedRevisionDoesNotTruncateToInteger() {
    repository.save(candidate("s1")); jdbc.update("update submission set revision=? where id='s1'",2147483648L);
    assertEquals(2147483648L,current().get("revision"));
  }
  @Test void storeReadCarriesRevision() {
    // Other Store sections are intentionally empty fixtures; only the real submission query matters here.
    var reader=spy(jdbc);
    emptyOtherReadSections(reader);
    repository.save(candidate("s1"));
    var loaded=new StoreService(reader,new ObjectMapper()).readStore(); assertEquals(0L,loaded.submissions.getFirst().get("revision"));
  }
  private void emptyOtherReadSections(JdbcTemplate reader) {
    for(String sql:List.of("select id,name from department order by id","select id,name,major,department_id from class_info order by id",
        "select id,role,username,name,department_id,class_id,major from user_account order by id","select * from question where deleted=0",
        "select * from paper where deleted=0","select * from exam where deleted=0 order by start_time desc,id",
        "select e.* from exam e where e.deleted=1 and exists (select 1 from submission s where s.exam_id=e.id) order by e.start_time desc,e.id","select exam_id,content_json from exam_snapshot",
        "select * from wrong_book_entry","select id,actor_id,action,detail,time from system_log order by time desc limit 100",
        "select id,teacher_id,questions_json,question_count,created_at from question_backup order by created_at desc","select * from notification order by created_at desc limit 200")) {
      doReturn(List.of()).when(reader).queryForList(sql);
    }
  }
  @Test void transactionCompletionEvictsConcurrentStaleCacheRefill() {
    storage.saveRecord("submissions",candidate("s1"));
    var stale=new StoreService.Store(); stale.submissions.add(new LinkedHashMap<>(current()));
    transaction.executeWithoutResult(status->{
      var r=candidate("s1"); r.put("revision",0L); r.put("status","已完成"); storage.saveRecord("submissions",r);
      ReflectionTestUtils.setField(storage,"cachedStore",stale);
      ReflectionTestUtils.setField(storage,"cachedStoreTimestamp",System.currentTimeMillis());
    });
    assertNull(ReflectionTestUtils.getField(storage,"cachedStore"));
  }
}
