package com.onlineexam.service;
import com.onlineexam.StoreService;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ResourceDeleteOwnershipTest {
  @ParameterizedTest @ValueSource(strings={"questions","papers","exams"})
  void teacherCannotDeleteAnotherTeachersResource(String entity){
    var view=new StoreService.Store();view.users.add(Map.of("id","t1","role","teacher"));
    view.entity(entity).add(Map.of("id","target","teacherId","t2"));
    var writes=new ArrayList<String>();
    var storage=mock(StoreService.class,inv->{
      if(inv.getMethod().getName().equals("readStore"))return view;
      if(Set.of("deleteRecord","deleteOwnedResource").contains(inv.getMethod().getName()))writes.add(inv.getMethod().getName());
      return org.mockito.Answers.RETURNS_DEFAULTS.answer(inv);
    });
    var logs=mock(SystemLogService.class);var service=new EntityCrudService(storage,mock(AuthService.class),logs);
    assertEquals(HttpStatus.FORBIDDEN,service.deleteEntity("t1",entity,"target").getStatusCode());
    assertTrue(writes.isEmpty());verifyNoInteractions(logs);
  }
}
