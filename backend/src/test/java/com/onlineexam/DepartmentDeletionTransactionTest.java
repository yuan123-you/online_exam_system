package com.onlineexam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.service.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class DepartmentDeletionTransactionTest {
  AnnotationConfigApplicationContext context; JdbcTemplate jdbc; Holder holder; EntityCrudService crud; StoreService storage; SystemLogService logs;
  static class Holder { StoreService.Store view=new StoreService.Store(); }
  static class Cached extends StoreService {
    final Holder holder; Cached(JdbcTemplate jdbc,Holder holder){super(jdbc,new ObjectMapper());this.holder=holder;}
    @Override public Store readStore(){return holder.view;}
  }
  @Configuration @EnableTransactionManagement(proxyTargetClass=true)
  static class Config {
    @Bean DataSource ds(){return new DriverManagerDataSource("jdbc:h2:mem:dept_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");}
    @Bean JdbcTemplate jdbc(DataSource ds){return spy(new JdbcTemplate(ds));}
    @Bean PlatformTransactionManager tx(DataSource ds){return new DataSourceTransactionManager(ds);}
    @Bean Holder holder(){return new Holder();}
    @Bean StoreService store(JdbcTemplate jdbc,Holder holder){return new Cached(jdbc,holder);}
    @Bean SystemLogService logs(){return mock(SystemLogService.class);}
    @Bean EntityCrudService crud(StoreService store,SystemLogService logs){return new EntityCrudService(store,mock(AuthService.class),logs);}
  }
  @BeforeEach void setup(){
    context=new AnnotationConfigApplicationContext(Config.class);jdbc=context.getBean(JdbcTemplate.class);holder=context.getBean(Holder.class);
    crud=context.getBean(EntityCrudService.class);storage=context.getBean(StoreService.class);logs=context.getBean(SystemLogService.class);
    jdbc.execute("create table department(id varchar(64) primary key,name varchar(100))");jdbc.update("insert into department values('d1','Department')");
    jdbc.execute("create table class_info(id varchar(64) primary key,department_id varchar(64) references department(id) on delete restrict)");
    jdbc.execute("create table user_account(id varchar(64) primary key,role varchar(20),department_id varchar(64) references department(id) on delete set null)");
    holder.view.users.add(Map.of("id","a1","role","admin"));holder.view.departments.add(new LinkedHashMap<>(Map.of("id","d1","name","Department")));
  }
  @AfterEach void close(){jdbc.execute("drop all objects");context.close();}
  org.springframework.http.ResponseEntity<?> remove(){return crud.deleteEntity("a1","departments","d1");}
  long departments(){return jdbc.queryForObject("select count(*) from department where id='d1'",Long.class);}
  @Test void currentTeacherBindingCannotBeNulledUsingCachedZeroReferenceView(){
    jdbc.update("insert into user_account values('t1','teacher','d1')");
    var before=new LinkedHashMap<>(holder.view.departments.getFirst());
    assertEquals(409,assertThrows(ResponseStatusException.class,this::remove).getStatusCode().value());
    assertEquals(1,departments());assertEquals("d1",jdbc.queryForObject("select department_id from user_account where id='t1'",String.class));
    assertEquals(before,holder.view.departments.getFirst());verifyNoInteractions(logs);
  }
  @Test void currentClassBindingReturnsConflictBeforePhysicalFKFailure(){
    jdbc.update("insert into class_info values('c1','d1')");
    assertEquals(409,assertThrows(ResponseStatusException.class,this::remove).getStatusCode().value());
    assertEquals(1,departments());assertEquals("d1",jdbc.queryForObject("select department_id from class_info where id='c1'",String.class));verifyNoInteractions(logs);
  }
  @Test void missingCurrentDepartmentDoesNotAuditFalseSuccess(){
    jdbc.update("delete from department where id='d1'");
    assertEquals(404,assertThrows(ResponseStatusException.class,this::remove).getStatusCode().value());verifyNoInteractions(logs);
  }
  @Test void cachedReferenceBlockerRemainsBadRequestAndNoWrites(){
    holder.view.users.add(Map.of("id","t1","role","teacher","departmentId","d1"));
    assertEquals(400,remove().getStatusCode().value());assertEquals(1,departments());verifyNoInteractions(logs);
  }
  @Test void noReferencesCanDeleteWithoutMutatingReadView(){
    assertEquals(200,remove().getStatusCode().value());assertEquals(0,departments());
    assertEquals("Department",holder.view.departments.getFirst().get("name"));verify(logs).log(anyMap(),eq("delete departments"),eq("d1"));
  }
  @Test void realAuditWriteAndDepartmentDeletionRollbackTogether(){
    jdbc.execute("create table system_log(id varchar(64) primary key,actor_id varchar(64),action varchar(100),detail varchar(1000),time timestamp)");
    doAnswer(inv->{new SystemLogService(storage).log(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2));
      assertEquals(1,jdbc.queryForObject("select count(*) from system_log",Integer.class));throw new IllegalStateException("audit fail");}).when(logs).log(anyMap(),anyString(),anyString());
    assertThrows(IllegalStateException.class,this::remove);assertEquals(1,departments());assertEquals(0,jdbc.queryForObject("select count(*) from system_log",Integer.class));
  }
  @Test void concurrentDepartmentBindingMustNotBecomeNullAfterDeletion() throws Exception {
    var atDelete=new CountDownLatch(1);var release=new CountDownLatch(1);var added=new CountDownLatch(1);
    doAnswer(inv->{atDelete.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return inv.callRealMethod();})
      .when(jdbc).update(startsWith("delete from department where id=?"),any(Object[].class));
    var pool=Executors.newFixedThreadPool(2);
    try {
      var deleting=pool.submit(()->{try{return remove().getStatusCode().value();}catch(ResponseStatusException e){return e.getStatusCode().value();}});
      assertTrue(atDelete.await(3,TimeUnit.SECONDS));
      var binding=pool.submit(()->{try{jdbc.update("insert into user_account values('late','teacher','d1')");return true;}
        catch(org.springframework.dao.DataIntegrityViolationException rejected){return false;}finally{added.countDown();}});
      added.await(300,TimeUnit.MILLISECONDS);release.countDown();
      int status=deleting.get(6,TimeUnit.SECONDS);boolean joined=binding.get(6,TimeUnit.SECONDS);
      assertTrue(status==200||status==409);
      assertEquals(0,jdbc.queryForObject("select count(*) from user_account where department_id is null",Integer.class));
      if(joined){assertEquals(1,departments());assertEquals("d1",jdbc.queryForObject("select department_id from user_account where id='late'",String.class));}
    } finally {release.countDown();pool.shutdownNow();}
  }
  @Test void zeroAffectedDeleteCannotReportSuccess(){
    doReturn(0).when(jdbc).update(startsWith("delete from department where id=?"),any(Object[].class));
    assertEquals(409,assertThrows(ResponseStatusException.class,this::remove).getStatusCode().value());assertEquals(1,departments());verifyNoInteractions(logs);
  }
}
