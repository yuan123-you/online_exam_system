package com.onlineexam.config;

import com.onlineexam.StoreService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAccountInitializerTest {
  @Test void noCredentialsMeansNoAccountCreation() {
    var jdbc = mock(JdbcTemplate.class);
    new AdminAccountInitializer(jdbc, mock(StoreService.class), "", "").run(null);
    verifyNoInteractions(jdbc);
  }
  @Test void partialCredentialsFailBeforeDatabaseAccess() {
    var jdbc = mock(JdbcTemplate.class);
    assertThrows(IllegalStateException.class, () ->
        new AdminAccountInitializer(jdbc, mock(StoreService.class), "operator", "").run(null));
    verifyNoInteractions(jdbc);
  }
  @Test void existingAccountIsNeverResetOnRestart() {
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("operator"))).thenReturn(1);
    new AdminAccountInitializer(jdbc, mock(StoreService.class), "operator", "test-only-input-not-a-credential").run(null);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
  @Test void newAccountReceivesOnlyABcryptHash() {
    var jdbc = mock(JdbcTemplate.class);
    var store = mock(StoreService.class);
    when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("operator"))).thenReturn(0);
    String input = "test-only-input-not-a-credential";
    new AdminAccountInitializer(jdbc, store, "operator", input).run(null);
    verify(jdbc).update(anyString(), anyString(), eq("operator"),
        argThat((String hash) -> !input.equals(hash) && new BCryptPasswordEncoder().matches(input, hash)));
    verify(store).invalidateCache();
  }
}
