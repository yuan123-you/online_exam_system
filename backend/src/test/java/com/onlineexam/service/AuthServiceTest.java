package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private StoreService storeService;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private AuthService authService;

    // ================================================================
    // Helper methods
    // ================================================================

    private Map<String, Object> userRow(String id, String role, String username, String password) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("role", role);
        row.put("username", username);
        row.put("password", password);
        row.put("name", "Test User");
        row.put("department_id", "dept1");
        row.put("class_id", "class1");
        row.put("major", "CS");
        return row;
    }

    private Map<String, Object> loginBody(String username, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        return body;
    }

    private Store createStore(Map<String, Object>... users) {
        Store store = new Store();
        store.users = new ArrayList<>(List.of(users));
        return store;
    }

    // ================================================================
    // Login tests
    // ================================================================

    @Nested
    class LoginTests {

        @Test
        void login_correctCredentials_returnsOkWithSanitizedUser() {
            String hashed = authService.hashPassword("password123");
            Map<String, Object> dbUser = userRow("u1", "admin", "admin", hashed);
            when(jdbc.queryForList(anyString(), eq("admin"))).thenReturn(List.of(dbUser));

            ResponseEntity<?> response = authService.login(loginBody("admin", "password123"));

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) body.get("user");
            assertNotNull(user);
            assertEquals("admin", user.get("username"));
            assertEquals("admin", user.get("role"));
            assertNull(user.get("password"), "Password should be removed from response");
            verify(systemLogService).log(any(), eq("login"), anyString());
        }

        @Test
        void login_wrongPassword_returnsUnauthorized() {
            String hashed = authService.hashPassword("correct");
            Map<String, Object> dbUser = userRow("u1", "student", "student1", hashed);
            when(jdbc.queryForList(anyString(), eq("student1"))).thenReturn(List.of(dbUser));

            ResponseEntity<?> response = authService.login(loginBody("student1", "wrong"));

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.get("message").toString().contains("账号或密码错误"));
        }

        @Test
        void login_userNotFound_returnsUnauthorized() {
            when(jdbc.queryForList(anyString(), eq("ghost"))).thenReturn(List.of());

            ResponseEntity<?> response = authService.login(loginBody("ghost", "any"));

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        void login_sixFailedAttempts_lockedForOneMinute() {
            when(jdbc.queryForList(anyString(), eq("victim"))).thenReturn(List.of());

            // First 5 attempts should return UNAUTHORIZED
            for (int i = 0; i < 5; i++) {
                ResponseEntity<?> r = authService.login(loginBody("victim", "bad"));
                assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode(), "Attempt " + (i + 1) + " should be UNAUTHORIZED");
            }

            // 6th attempt should be locked
            ResponseEntity<?> locked = authService.login(loginBody("victim", "bad"));
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, locked.getStatusCode());
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) locked.getBody();
            assertNotNull(body);
            assertTrue(body.get("message").toString().contains("登录尝试次数过多"));
        }

        @Test
        void login_successAfterFourFailures_clearsFailedAttempts() {
            String hashed = authService.hashPassword("correct");
            Map<String, Object> dbUser = userRow("u1", "student", "stu", hashed);
            when(jdbc.queryForList(anyString(), eq("stu"))).thenReturn(List.of(dbUser));

            // 4 failed attempts (below threshold of 5)
            for (int i = 0; i < 4; i++) {
                authService.login(loginBody("stu", "wrong"));
            }

            // Successful login clears the counter
            ResponseEntity<?> response = authService.login(loginBody("stu", "correct"));
            assertEquals(HttpStatus.OK, response.getStatusCode());

            // Now 4 more wrong attempts should NOT lock the account (counter was reset)
            for (int i = 0; i < 4; i++) {
                ResponseEntity<?> r = authService.login(loginBody("stu", "wrong"));
                assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode(), "Post-reset attempt " + (i + 1) + " should be UNAUTHORIZED, not locked");
            }
        }

        @Test
        void login_blankUsername_noLockout() {
            when(jdbc.queryForList(anyString(), eq(""))).thenReturn(List.of());

            // Even many failed attempts with blank username should not lock
            for (int i = 0; i < 10; i++) {
                ResponseEntity<?> r = authService.login(loginBody("", "bad"));
                assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
            }
        }

        @Test
        void login_plainTextPassword_autoUpgradesToBCrypt() {
            // Simulate a user stored with plain-text password
            Map<String, Object> dbUser = userRow("u1", "teacher", "teacher1", "plaintext");
            when(jdbc.queryForList(anyString(), eq("teacher1"))).thenReturn(List.of(dbUser));

            when(jdbc.update(eq("update user_account set password=? where id=? and password=?"),
                anyString(), eq("u1"), eq("plaintext"))).thenReturn(1);

            ResponseEntity<?> response = authService.login(loginBody("teacher1", "plaintext"));

            assertEquals(HttpStatus.OK, response.getStatusCode());
            // Login upgrades only the password column, not a cached user record.
            verify(jdbc).update(eq("update user_account set password=? where id=? and password=?"),
                argThat((String hash) -> authService.matchesPassword("plaintext", hash)
                    && !authService.needsPasswordUpgrade(hash)), eq("u1"), eq("plaintext"));
            verifyNoInteractions(storeService);
        }
    }

    // ================================================================
    // Change Password tests
    // ================================================================

    @Nested
    class ChangePasswordTests {

        @Test
        void changePassword_validInput_returnsOk() {
            String hashed = authService.hashPassword("oldPass123");
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", "u1");
            user.put("username", "user1");
            // StoreService intentionally excludes credentials from its cached users.
            when(jdbc.queryForList(eq("select password from user_account where id=? limit 1"), eq("u1")))
                .thenReturn(List.of(Map.of("password", hashed)));
            user.put("role", "student");
            user.put("name", "User One");
            Store store = createStore(user);
            when(storeService.readStore()).thenReturn(store);

            when(jdbc.update(eq("update user_account set password=? where id=? and password=?"),
                anyString(), eq("u1"), eq(hashed))).thenReturn(1);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("oldPassword", "oldPass123");
            body.put("newPassword", "newPass456");

            ResponseEntity<?> response = authService.changePassword("u1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(jdbc).update(eq("update user_account set password=? where id=? and password=?"),
                argThat((String hash) -> authService.matchesPassword("newPass456", hash)
                    && !authService.needsPasswordUpgrade(hash)), eq("u1"), eq(hashed));
            verify(storeService).invalidateCache();
            verify(storeService, never()).saveRecord(anyString(), any());
            assertFalse(user.containsKey("password"));
            verify(systemLogService).log(any(), eq("change password"), anyString());
        }

        @Test
        void changePassword_wrongOldPassword_returnsBadRequest() {
            String hashed = authService.hashPassword("realOld");
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", "u1");
            user.put("username", "user1");
            // StoreService intentionally excludes credentials from its cached users.
            when(jdbc.queryForList(eq("select password from user_account where id=? limit 1"), eq("u1")))
                .thenReturn(List.of(Map.of("password", hashed)));
            Store store = createStore(user);
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = Map.of("oldPassword", "wrongOld", "newPassword", "newPass456");
            ResponseEntity<?> response = authService.changePassword("u1", body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            verify(jdbc, never()).update(anyString(), any(Object[].class));
            verify(storeService, never()).saveRecord(anyString(), any());
            verify(storeService, never()).invalidateCache();
            verifyNoInteractions(systemLogService);
        }

        @Test
        void changePassword_userNotFound_returnsUnauthorized() {
            Store store = createStore(); // empty users
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = Map.of("oldPassword", "old", "newPassword", "newPass");
            ResponseEntity<?> response = authService.changePassword("nonexistent", body);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        void changePassword_newPasswordTooShort_returnsBadRequest() {
            String hashed = authService.hashPassword("oldPass");
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", "u1");
            user.put("username", "user1");
            // StoreService intentionally excludes credentials from its cached users.
            when(jdbc.queryForList(eq("select password from user_account where id=? limit 1"), eq("u1")))
                .thenReturn(List.of(Map.of("password", hashed)));
            Store store = createStore(user);
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = Map.of("oldPassword", "oldPass", "newPassword", "12345");
            ResponseEntity<?> response = authService.changePassword("u1", body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            verify(jdbc, never()).update(anyString(), any(Object[].class));
            verify(storeService, never()).saveRecord(anyString(), any());
            verify(storeService, never()).invalidateCache();
            verifyNoInteractions(systemLogService);
        }

        @Test
        void changePassword_newPasswordTooLong_returnsBadRequest() {
            String hashed = authService.hashPassword("oldPass");
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", "u1");
            user.put("username", "user1");
            // StoreService intentionally excludes credentials from its cached users.
            when(jdbc.queryForList(eq("select password from user_account where id=? limit 1"), eq("u1")))
                .thenReturn(List.of(Map.of("password", hashed)));
            Store store = createStore(user);
            when(storeService.readStore()).thenReturn(store);

            String longPassword = "a".repeat(101);
            Map<String, Object> body = Map.of("oldPassword", "oldPass", "newPassword", longPassword);
            ResponseEntity<?> response = authService.changePassword("u1", body);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            verify(jdbc, never()).update(anyString(), any(Object[].class));
            verify(storeService, never()).saveRecord(anyString(), any());
            verify(storeService, never()).invalidateCache();
            verifyNoInteractions(systemLogService);
        }
    }

    // ================================================================
    // Reset Password tests
    // ================================================================

    @Nested
    class ResetPasswordTests {

        @Test
        void resetPassword_adminResetsWithCustomPassword_returnsOk() {
            Map<String, Object> admin = new LinkedHashMap<>();
            admin.put("id", "a1");
            admin.put("username", "admin1");
            admin.put("role", "admin");
            admin.put("name", "Admin");

            Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", "t1");
            target.put("username", "target1");
            target.put("role", "student");
            target.put("name", "Target");

            Store store = createStore(admin, target);
            when(storeService.readStore()).thenReturn(store);

            when(jdbc.update(eq("update user_account set password=? where id=?"),
                anyString(), eq("t1"))).thenReturn(1);

            Map<String, Object> body = Map.of("userId", "t1", "newPassword", "newPass123");
            ResponseEntity<?> response = authService.resetPassword("a1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(jdbc).update(eq("update user_account set password=? where id=?"),
                argThat((String hash) -> authService.matchesPassword("newPass123", hash)
                    && !authService.needsPasswordUpgrade(hash)), eq("t1"));
            assertFalse(target.containsKey("password"));
            verify(storeService).invalidateCache();
            verify(storeService, never()).saveRecord(anyString(), any());
            verify(systemLogService).log(eq(admin), eq("reset password"), eq("target1"));
        }

        @Test
        void resetPassword_blankNewPassword_usesDefault123456() {
            Map<String, Object> admin = new LinkedHashMap<>();
            admin.put("id", "a1");
            admin.put("username", "admin1");
            admin.put("role", "admin");
            admin.put("password", "hashed");
            admin.put("name", "Admin");

            Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", "t1");
            target.put("username", "target1");
            target.put("role", "student");
            target.put("password", "oldHash");
            target.put("name", "Target");

            Store store = createStore(admin, target);
            when(storeService.readStore()).thenReturn(store);
            when(jdbc.update(eq("update user_account set password=? where id=?"), anyString(), eq("t1"))).thenReturn(1);

            Map<String, Object> body = Map.of("userId", "t1", "newPassword", "");
            ResponseEntity<?> response = authService.resetPassword("a1", body);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            // Default password "123456" should be hashed and saved via JDBC
            verify(jdbc).update(eq("update user_account set password=? where id=?"),
                argThat((String hash) -> authService.matchesPassword("123456", hash)
                    && !authService.needsPasswordUpgrade(hash)), eq("t1"));
            verify(storeService).invalidateCache();
            verify(storeService, never()).saveRecord(anyString(), any());
        }

        @Test
        void resetPassword_nonAdmin_returnsForbidden() {
            Map<String, Object> teacher = new LinkedHashMap<>();
            teacher.put("id", "t1");
            teacher.put("username", "teacher1");
            teacher.put("role", "teacher");
            teacher.put("password", "hashed");
            teacher.put("name", "Teacher");

            Store store = createStore(teacher);
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = Map.of("userId", "t1", "newPassword", "newPass");
            ResponseEntity<?> response = authService.resetPassword("t1", body);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }

        @Test
        void resetPassword_targetUserNotFound_returnsNotFound() {
            Map<String, Object> admin = new LinkedHashMap<>();
            admin.put("id", "a1");
            admin.put("username", "admin1");
            admin.put("role", "admin");
            admin.put("password", "hashed");
            admin.put("name", "Admin");

            Store store = createStore(admin);
            when(storeService.readStore()).thenReturn(store);

            Map<String, Object> body = Map.of("userId", "ghost", "newPassword", "newPass123");
            ResponseEntity<?> response = authService.resetPassword("a1", body);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }

    // ================================================================
    // Password utility tests
    // ================================================================

    @Nested
    class PasswordUtilTests {

        @Test
        void matchesPassword_bcryptHash_correctPassword_returnsTrue() {
            String hashed = authService.hashPassword("secret");
            assertTrue(authService.matchesPassword("secret", hashed));
        }

        @Test
        void matchesPassword_bcryptHash_wrongPassword_returnsFalse() {
            String hashed = authService.hashPassword("secret");
            assertFalse(authService.matchesPassword("wrong", hashed));
        }

        @Test
        void matchesPassword_plainText_matchingPassword_returnsTrue() {
            assertTrue(authService.matchesPassword("plaintext", "plaintext"));
        }

        @Test
        void matchesPassword_plainText_nonMatchingPassword_returnsFalse() {
            assertFalse(authService.matchesPassword("abc", "xyz"));
        }

        @Test
        void hashPassword_producesValidBCryptHash() {
            String hash = authService.hashPassword("mypassword");
            assertNotNull(hash);
            assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));
            assertTrue(authService.matchesPassword("mypassword", hash));
        }

        @Test
        void needsPasswordUpgrade_plainText_returnsTrue() {
            assertTrue(authService.needsPasswordUpgrade("plaintext"));
        }

        @Test
        void needsPasswordUpgrade_bcrypt2a_returnsFalse() {
            String hash = authService.hashPassword("test");
            assertFalse(authService.needsPasswordUpgrade(hash));
        }

        @Test
        void needsPasswordUpgrade_null_returnsFalse() {
            assertFalse(authService.needsPasswordUpgrade(null));
        }

        @Test
        void needsPasswordUpgrade_bcrypt2bPrefix_returnsFalse() {
            assertFalse(authService.needsPasswordUpgrade("$2b$10$abcdefghijklmnopqrstuu"));
        }
    }
}
