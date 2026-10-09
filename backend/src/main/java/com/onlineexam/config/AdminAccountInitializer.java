package com.onlineexam.config;

import com.onlineexam.StoreService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Optional, explicit bootstrap; never resets an existing account or logs credentials. */
@Component
public class AdminAccountInitializer implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final StoreService store;
  private final String username;
  private final String password;

  public AdminAccountInitializer(JdbcTemplate jdbc, StoreService store,
      @Value("${ADMIN_USERNAME:}") String username, @Value("${ADMIN_PASSWORD:}") String password) {
    this.jdbc = jdbc; this.store = store; this.username = username; this.password = password;
  }

  @Override public void run(ApplicationArguments args) {
    if (username.isBlank() && password.isBlank()) return;
    if (username.isBlank() || password.isBlank() || password.length() < 8
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalStateException("Explicit ADMIN_USERNAME and a valid ADMIN_PASSWORD are required");
    }
    Integer count = jdbc.queryForObject("select count(*) from user_account where username=?", Integer.class, username);
    if (count != null && count > 0) return;
    String hash = new BCryptPasswordEncoder().encode(password);
    jdbc.update("insert into user_account (id,role,username,password,name) values (?,'admin',?,?,'Administrator')",
        "admin_" + UUID.randomUUID().toString().replace("-", ""), username, hash);
    store.invalidateCache();
  }
}
