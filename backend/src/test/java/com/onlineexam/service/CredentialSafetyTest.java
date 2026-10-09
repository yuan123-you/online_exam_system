package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CredentialSafetyTest {
  @Test void blankStoredPasswordsCannotAuthenticate() {
    var auth = new AuthService(mock(StoreService.class), mock(SystemLogService.class), mock(JdbcTemplate.class));
    assertFalse(auth.matchesPassword("", ""));
    assertFalse(auth.matchesPassword(null, null));
    assertFalse(auth.matchesPassword("any", ""));
  }

  @Test void resetRequiresExplicitPassword() {
    var storeService = mock(StoreService.class);
    var store = new Store();
    store.users = new ArrayList<>(List.of(
        new LinkedHashMap<>(Map.of("id", "operator", "role", "admin")),
        new LinkedHashMap<>(Map.of("id", "target", "role", "student"))));
    when(storeService.readStore()).thenReturn(store);
    var auth = new AuthService(storeService, mock(SystemLogService.class), mock(JdbcTemplate.class));
    assertEquals(HttpStatus.BAD_REQUEST, auth.resetPassword("operator", Map.of("userId", "target")).getStatusCode());
    verify(storeService, never()).saveRecord(anyString(), anyMap());
  }

  @Test void userCreationRejectsBlankPasswordBeforeHashing() {
    var storeService = mock(StoreService.class);
    var store = new Store();
    store.users = new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id", "operator", "role", "admin"))));
    when(storeService.readStore()).thenReturn(store);
    var auth = mock(AuthService.class);
    var crud = new EntityCrudService(storeService, auth, mock(SystemLogService.class));
    var result = crud.createEntity("operator", Map.of("entity", "users", "record",
        Map.of("username", "new-user", "name", "User", "role", "student", "password", "")));
    assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    verify(auth, never()).hashPassword(anyString());
    verify(storeService, never()).createRecord(anyString(), anyMap());
    verify(storeService, never()).saveRecord(anyString(), anyMap());
  }
}
