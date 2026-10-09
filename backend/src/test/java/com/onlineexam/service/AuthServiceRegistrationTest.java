package com.onlineexam.service;

import com.onlineexam.StoreService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceRegistrationTest {
  JdbcTemplate jdbc;
  AuthService service;
  StoreService store;
  Map<String,Object> body;
  @BeforeEach void setup() {
    jdbc=mock(JdbcTemplate.class);
    store=mock(StoreService.class);
    service=new AuthService(store,mock(SystemLogService.class),jdbc);
    body=new LinkedHashMap<>(Map.of("username","student_new","password","StudentCheck123!","name","测试学生","departmentId","d1","classId","c1"));
  }
  void eligible() {
    when(jdbc.queryForObject(contains("username"),eq(Integer.class),eq("student_new"))).thenReturn(0);
    when(jdbc.queryForList(contains("class_info"),eq("c1"),eq("d1"))).thenReturn(List.of(Map.of("id","c1","department_id","d1","major","计算机科学与技术")));
    when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
  }
  @Test void createsOnlyStudentWithHashedPasswordAndSafeResponse() {
    eligible(); body.put("role","admin");body.put("id","admin");
    var response=service.registerStudent(body);
    assertEquals(HttpStatus.CREATED,response.getStatusCode());
    @SuppressWarnings("unchecked") var result=(Map<String,Object>)response.getBody();
    @SuppressWarnings("unchecked") var user=(Map<String,Object>)result.get("user");
    verify(store).invalidateCache();
    assertEquals("student",user.get("role"));assertNotEquals("admin",user.get("id"));assertFalse(user.containsKey("password"));
    verify(jdbc).update(contains("insert into user_account"),any(),eq("student"),eq("student_new"),argThat((Object hash)->hash instanceof String && service.matchesPassword("StudentCheck123!",(String)hash)),eq("测试学生"),eq("d1"),eq("c1"),eq("计算机科学与技术"));
  }
  @Test void defaultsToExistingFiftyStudentClassAndHashesDefaultPassword() {
    body.remove("password"); body.remove("departmentId"); body.remove("classId");
    when(jdbc.queryForObject(contains("username"),eq(Integer.class),eq("student_new"))).thenReturn(0);
    when(jdbc.queryForList(contains("class_info"),eq("classe-1779258228737-4b1d9a"),eq("dept-1"))).thenReturn(List.of(Map.of("id","classe-1779258228737-4b1d9a","department_id","dept-1","major","计算机科学与技术（专升本）")));
    when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(1);
    assertEquals(HttpStatus.CREATED,service.registerStudent(body).getStatusCode());
    verify(jdbc).update(contains("insert into user_account"),any(),eq("student"),eq("student_new"),argThat((Object hash)->hash instanceof String && service.matchesPassword("123456",(String)hash)),eq("测试学生"),eq("dept-1"),eq("classe-1779258228737-4b1d9a"),eq("计算机科学与技术（专升本）"));
  }
  @Test void exposesExactDefaultClassForRegistrationForm() {
    @SuppressWarnings("unchecked") var options=(Map<String,Object>)service.registrationOptions().getBody();
    assertEquals("classe-1779258228737-4b1d9a",options.get("defaultClassId"));
  }
  @Test void rejectsDuplicateUsername() {
    when(jdbc.queryForObject(contains("username"),eq(Integer.class),eq("student_new"))).thenReturn(1);
    assertEquals(HttpStatus.CONFLICT,service.registerStudent(body).getStatusCode());
    verify(jdbc,never()).update(anyString(),any(Object[].class));
  }
  @Test void duplicateRaceReturnsConflictInsteadOfServerError() {
    eligible();when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any(),any(),any())).thenThrow(new DuplicateKeyException("username"));
    assertEquals(HttpStatus.CONFLICT,service.registerStudent(body).getStatusCode());
  }
  @Test void rejectsClassOutsideDepartment() {
    when(jdbc.queryForObject(contains("username"),eq(Integer.class),eq("student_new"))).thenReturn(0);
    when(jdbc.queryForList(contains("class_info"),eq("c1"),eq("d1"))).thenReturn(List.of());
    assertEquals(HttpStatus.BAD_REQUEST,service.registerStudent(body).getStatusCode());
  }
  @Test void rejectsInvalidUsernameBeforeAnyDatabaseWork() {
    body.put("username","bad';--");assertEquals(HttpStatus.BAD_REQUEST,service.registerStudent(body).getStatusCode());verifyNoInteractions(jdbc);
  }
  @Test void rejectsShortPasswordBeforeAnyDatabaseWork() {
    body.put("password","123");assertEquals(HttpStatus.BAD_REQUEST,service.registerStudent(body).getStatusCode());verifyNoInteractions(jdbc);
  }
  @Test void rejectsBcryptByteTruncation() {
    body.put("password","密".repeat(25));assertEquals(HttpStatus.BAD_REQUEST,service.registerStudent(body).getStatusCode());verifyNoInteractions(jdbc);
  }
  @Test void rejectsMissingNameAndClass() {
    body.put("name","");assertEquals(HttpStatus.BAD_REQUEST,service.registerStudent(body).getStatusCode());verifyNoInteractions(jdbc);
  }
}
