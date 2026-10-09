package com.onlineexam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileWriteIntegrityTest {
  SingleConnectionDataSource database;
  JdbcTemplate jdbc;
  StoreService persistence;
  @BeforeEach void setUp() {
    database=new SingleConnectionDataSource("jdbc:h2:mem:profile_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","",true);
    jdbc=new JdbcTemplate(database);
    jdbc.execute("create table user_account(id varchar(64) primary key,role varchar(20),username varchar(50) unique,password varchar(255) not null,name varchar(50),department_id varchar(64),class_id varchar(64),major varchar(100))");
    jdbc.update("insert into user_account values('u1','student','student1','original-hash','Original','d1','c1','Original Major')");
    persistence=new StoreService(jdbc,new ObjectMapper());
  }
  @AfterEach void close() { database.destroy(); }
  Map<String,Object> source() {
    return new LinkedHashMap<>(Map.of("id","u1","role","student","username","student1","name","Original","departmentId","d1","classId","c1","major","Original Major"));
  }
  StoreService.Store view() {
    var view=new StoreService.Store(); view.users.add(Map.of("id","a1","role","admin","name","Admin")); view.users.add(source());
    view.classes.add(Map.of("id","c1","departmentId","d1")); view.departments.add(Map.of("id","d1")); return view;
  }
  EntityCrudService crud(StoreService storage,StoreService.Store view,AuthService auth) {
    var logs=mock(SystemLogService.class);
    when(storage.readStore()).thenReturn(view); return new EntityCrudService(storage,auth,logs);
  }
  @Test void passwordFreeProfilePatchCanUpdateNameWithoutReadingOrWritingCredential() {
    var storage=mock(StoreService.class); var auth=mock(AuthService.class); var view=view();
    var response=crud(storage,view,auth).updateEntity("a1","users",Map.of("id","u1","name","Edited"));
    assertEquals(HttpStatus.OK,response.getStatusCode());
    assertFalse(((Map<?,?>)((Map<?,?>)response.getBody()).get("record")).containsKey("password"));
    assertEquals("Original",view.users.get(1).get("name")); verifyNoInteractions(auth);
  }
  @Test void explicitNonblankPasswordIsHashedAndOnlyRequestedFieldsAreWritten() {
    var storage=mock(StoreService.class); var auth=mock(AuthService.class); var view=view();
    when(auth.hashPassword("replacement")).thenReturn("safe-hash");
    var response=crud(storage,view,auth).updateEntity("a1","users",Map.of("id","u1","name","Edited","password","replacement"));
    assertEquals(HttpStatus.OK,response.getStatusCode());
    var captured=org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(storage).updateUserRecord(captured.capture(),eq(view.users.get(1)));
    assertEquals(Map.of("id","u1","name","Edited","password","safe-hash"),captured.getValue());
    assertFalse(((Map<?,?>)((Map<?,?>)response.getBody()).get("record")).containsKey("password"));
  }
  @Test void blankProfilePasswordMeansPreserveExistingCredentialNotGrantDefault() {
    var storage=mock(StoreService.class); var auth=mock(AuthService.class); var view=view();
    var response=crud(storage,view,auth).updateEntity("a1","users",Map.of("id","u1","name","Edited","password",""));
    assertEquals(HttpStatus.OK,response.getStatusCode());
    var captured=org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(storage).updateUserRecord(captured.capture(),eq(view.users.get(1))); assertEquals(Map.of("id","u1","name","Edited"),captured.getValue());
    verifyNoInteractions(auth);
  }
  private void updateProfile(Map<String,Object> patch) {
    // Reflection allows the intended production interface to be absent during RED.
    try { StoreService.class.getMethod("updateUserRecord",Map.class,Map.class).invoke(persistence,patch,source()); }
    catch(java.lang.reflect.InvocationTargetException e) {
      if(e.getCause() instanceof RuntimeException r) throw r; throw new RuntimeException(e.getCause());
    } catch(ReflectiveOperationException e) { throw new AssertionError("Existing-account patch interface is missing",e); }
  }
  @Test void SQLPatchPreservesPasswordAndConcurrentUnrequestedProfileChanges() {
    jdbc.update("update user_account set major='Concurrent Major',password='newer-hash' where id='u1'");
    updateProfile(Map.of("id","u1","name","Edited"));
    var row=jdbc.queryForMap("select * from user_account where id='u1'");
    assertEquals("Edited",row.get("name")); assertEquals("Concurrent Major",row.get("major")); assertEquals("newer-hash",row.get("password"));
  }
  @Test void deletedAccountUpdateCannotRecreateIt() {
    jdbc.update("delete from user_account where id='u1'");
    var error=assertThrows(ResponseStatusException.class,()->updateProfile(Map.of("id","u1","name","Edited")));
    assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode()); assertEquals(0,jdbc.queryForObject("select count(*) from user_account",Integer.class));
  }
  @Test void duplicateCreateIdCannotReplaceExistingAccount() {
    var create=source(); create.put("password","replacement"); create.put("name","Overwritten");
    assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->persistence.saveRecord("users",create)).getStatusCode());
    assertEquals("original-hash",jdbc.queryForObject("select password from user_account where id='u1'",String.class));
    assertEquals("Original",jdbc.queryForObject("select name from user_account where id='u1'",String.class));
  }
  @Test void duplicateCreateUsernameCannotOverwriteOtherID() {
    var create=source(); create.put("id","u2"); create.put("password","replacement");
    assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->persistence.saveRecord("users",create)).getStatusCode());
    assertEquals(1,jdbc.queryForObject("select count(*) from user_account",Integer.class));
  }
  @Test void explicitProfileCredentialHashCanBeUpdatedButUnknownFieldsCannotGrantIdentity() {
    updateProfile(Map.of("id","u1","password","new-hash","studentId","u2","isAdmin",true));
    assertEquals("new-hash",jdbc.queryForObject("select password from user_account where id='u1'",String.class));
    assertEquals("student",jdbc.queryForObject("select role from user_account where id='u1'",String.class));
  }
  @Test void staleValidationCannotClearBindingAfterConcurrentRoleTransition() {
    updateProfile(Map.of("id","u1","role","teacher","departmentId","d1"));
    var staleStudentPatch=new LinkedHashMap<String,Object>(); staleStudentPatch.put("id","u1"); staleStudentPatch.put("departmentId",null);
    // Both requests validated the original student with c1/d1; the second must not merge into a new teacher.
    var error=assertThrows(ResponseStatusException.class,()->updateProfile(staleStudentPatch));
    assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
    assertEquals("teacher",jdbc.queryForObject("select role from user_account where id='u1'",String.class));
    assertEquals("d1",jdbc.queryForObject("select department_id from user_account where id='u1'",String.class));
  }
  @Test void concurrentUsernameCollisionIsAnExplicitConflictNotServerFailure() {
    jdbc.update("insert into user_account values('u2','student','taken','other-hash','Other','d1','c1','Other Major')");
    var error=assertThrows(ResponseStatusException.class,()->updateProfile(Map.of("id","u1","username","taken")));
    assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
    assertEquals("student1",jdbc.queryForObject("select username from user_account where id='u1'",String.class));
  }
  @Test void nonemptyUpdateReportingZeroCanSucceedOnlyWhenRequestedValueAlreadyMatches() {
    var zero=spy(jdbc);
    doReturn(0).when(zero).update(startsWith("update user_account set"),any(Object[].class));
    persistence=new StoreService(zero,new ObjectMapper());
    assertDoesNotThrow(()->updateProfile(Map.of("id","u1","name","Original")));
    var conflict=assertThrows(ResponseStatusException.class,()->updateProfile(Map.of("id","u1","name","Edited")));
    assertEquals(HttpStatus.CONFLICT,conflict.getStatusCode());
    assertEquals("Original",jdbc.queryForObject("select name from user_account where id='u1'",String.class));
  }
  @Test void zeroChangedNoopCannotBypassChangedValidationTuple() {
    jdbc.update("update user_account set role='teacher' where id='u1'");
    var zero=spy(jdbc);
    doReturn(0).when(zero).update(startsWith("update user_account set"),any(Object[].class));
    persistence=new StoreService(zero,new ObjectMapper());
    assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
        ()->updateProfile(Map.of("id","u1","name","Original"))).getStatusCode());
  }
  @Test void noOpExistingPatchStillSucceedsWithoutChangingData() {
    assertDoesNotThrow(()->updateProfile(Map.of("id","u1")));
    assertEquals("Original",jdbc.queryForObject("select name from user_account where id='u1'",String.class));
  }
}
