package com.onlineexam.service;

import com.onlineexam.StoreService;
import com.onlineexam.StoreService.Store;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SQL/BCrypt contracts; only the password-free store and audit sink are mocked. */
class AuthServicePersistenceContractTest {
    private SingleConnectionDataSource database;
    private JdbcTemplate jdbc;
    private StoreService store;
    private SystemLogService logs;
    private SessionTokenService sessions;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        database = new SingleConnectionDataSource(
            "jdbc:h2:mem:auth_contract_" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE", "sa", "", true);
        jdbc = spy(new JdbcTemplate(database));
        jdbc.execute("""
            create table user_account (
              id varchar(64) primary key, role varchar(32), username varchar(64),
              password varchar(255), name varchar(64), department_id varchar(64),
              class_id varchar(64), major varchar(64))
            """);
        store = mock(StoreService.class);
        logs = mock(SystemLogService.class);
        sessions = spy(new SessionTokenService());
        auth = new AuthService(store, logs, jdbc, sessions);
    }

    @AfterEach
    void closeDatabase() {
        database.destroy();
    }

    private void insertUser(String id, String username, String password) {
        jdbc.update("insert into user_account values (?,?,?,?,?,?,?,?)",
            id, "student", username, password, "Original Name", "dept1", "class1", "CS");
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("select * from user_account where id=?", id);
    }

    private Map<String, Object> cachedUser() {
        Map<String, Object> user = new LinkedHashMap<>(row("u1"));
        user.remove("password");
        Store snapshot = new Store();
        snapshot.users = List.of(user);
        when(store.readStore()).thenReturn(snapshot);
        return user;
    }

    @Test
    void plaintextLoginUpgradesOnlyTargetPasswordAndReturnsSanitizedSession() {
        insertUser("u1", "student1", "oldPass123");
        insertUser("u2", "student2", "untouched-password");
        Map<String, Object> before = row("u1");
        Map<String, Object> other = row("u2");

        var response = auth.login(Map.of("username", "student1", "password", "oldPass123"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        Map<?, ?> user = (Map<?, ?>) body.get("user");
        assertFalse(user.containsKey("password"));
        assertEquals("dept1", user.get("departmentId"));
        assertEquals("class1", user.get("classId"));
        assertFalse(user.containsKey("department_id"));
        assertFalse(user.containsKey("class_id"));
        assertTrue(sessions.validate((String) body.get("sessionToken"), "u1"));
        Map<String, Object> after = row("u1");
        String hash = (String) after.get("password");
        assertFalse(auth.needsPasswordUpgrade(hash));
        assertTrue(auth.matchesPassword("oldPass123", hash));
        after.put("password", before.get("password"));
        assertEquals(before, after, "Legacy upgrade must preserve every profile column");
        assertEquals(other, row("u2"));
        verifyNoInteractions(store);
    }

    @Test
    void bcryptLoginDoesNotRewriteCredential() {
        String hash = auth.hashPassword("oldPass123");
        insertUser("u1", "student1", hash);
        Map<String, Object> before = row("u1");

        assertEquals(HttpStatus.OK, auth.login(
            Map.of("username", "student1", "password", "oldPass123")).getStatusCode());

        assertEquals(before, row("u1"));
        verifyNoInteractions(store);
    }

    @Test
    void changePasswordUsesDatabaseCredentialWithoutCachedPasswordAndPreservesProfile() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        insertUser("u2", "student2", "untouched-password");
        Map<String, Object> cached = cachedUser();
        Map<String, Object> before = row("u1");
        Map<String, Object> other = row("u2");

        var response = auth.changePassword("u1",
            Map.of("oldPassword", "oldPass123", "newPassword", "newPass456"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> after = row("u1");
        String hash = (String) after.get("password");
        assertFalse(auth.needsPasswordUpgrade(hash));
        assertTrue(auth.matchesPassword("newPass456", hash));
        assertFalse(auth.matchesPassword("oldPass123", hash));
        after.put("password", before.get("password"));
        assertEquals(before, after);
        assertEquals(other, row("u2"));
        assertFalse(cached.containsKey("password"));
        verify(store).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verify(logs).log(any(), eq("change password"), eq("student1"));
    }

    @Test
    void wrongOldPasswordDoesNotModifyDatabaseOrInvalidateCache() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        Map<String, Object> before = row("u1");

        assertEquals(HttpStatus.BAD_REQUEST, auth.changePassword("u1",
            Map.of("oldPassword", "incorrect", "newPassword", "newPass456")).getStatusCode());

        assertEquals(before, row("u1"));
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }

    @Test
    void deletedDatabaseAccountCannotChangePasswordDespiteCachedIdentity() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        jdbc.update("delete from user_account where id=?", "u1");

        assertEquals(HttpStatus.UNAUTHORIZED, auth.changePassword("u1",
            Map.of("oldPassword", "oldPass123", "newPassword", "newPass456")).getStatusCode());

        assertEquals(0, jdbc.queryForObject("select count(*) from user_account", Integer.class));
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }

    private Map<String, Object> cachedResetTarget() {
        Map<String, Object> target = new LinkedHashMap<>(row("u1"));
        target.remove("password");
        Store snapshot = new Store();
        snapshot.users = List.of(Map.of("id", "a1", "role", "admin", "username", "admin1"), target);
        when(store.readStore()).thenReturn(snapshot);
        return target;
    }

    @Test
    void resetWritesOnlyPasswordWithoutMutatingCacheOrOverwritingConcurrentProfileChange() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        insertUser("u2", "student2", "untouched-password");
        Map<String, Object> target = cachedResetTarget();
        Map<String, Object> cachedBefore = new LinkedHashMap<>(target);
        jdbc.update("update user_account set name=?,department_id=?,class_id=?,major=? where id=?",
            "New Name", "new-dept", "new-class", "New Major", "u1");
        Map<String, Object> before = row("u1");
        Map<String, Object> other = row("u2");

        var response = auth.resetPassword("a1", Map.of("userId", "u1", "newPassword", "newPass456"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(cachedBefore, target, "Reset must not put credentials into a shared cached row");
        Map<String, Object> after = row("u1");
        String hash = (String) after.get("password");
        assertTrue(auth.matchesPassword("newPass456", hash));
        assertFalse(auth.needsPasswordUpgrade(hash));
        assertFalse(auth.matchesPassword("oldPass123", hash));
        after.put("password", before.get("password"));
        assertEquals(before, after, "Reset must preserve the latest DB profile, not the cached profile");
        assertEquals(other, row("u2"));
        verify(store).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verify(logs).log(any(), eq("reset password"), eq("student1"));
    }

    @Test
    void resetOfDeletedDatabaseTargetReturnsNotFoundWithoutSuccessAuditOrCachedMutation() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        Map<String, Object> target = cachedResetTarget();
        Map<String, Object> before = new LinkedHashMap<>(target);
        jdbc.update("delete from user_account where id=?", "u1");

        assertEquals(HttpStatus.NOT_FOUND, auth.resetPassword("a1",
            Map.of("userId", "u1", "newPassword", "newPass456")).getStatusCode());

        assertEquals(before, target);
        assertEquals(0, jdbc.queryForObject("select count(*) from user_account", Integer.class));
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }

    @Test
    void concurrentPasswordReplacementCannotBeOverwrittenUsingStaleOldPasswordProof() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        String replacement = auth.hashPassword("concurrentReset456");
        // Read the original credential, then deterministically interleave another writer.
        doAnswer(invocation -> {
            Object originalRows = invocation.callRealMethod();
            new JdbcTemplate(database).update("update user_account set password=? where id=?", replacement, "u1");
            return originalRows;
        }).when(jdbc).queryForList(eq("select password from user_account where id=? limit 1"), eq("u1"));

        assertEquals(HttpStatus.CONFLICT, auth.changePassword("u1",
            Map.of("oldPassword", "oldPass123", "newPassword", "newPass456")).getStatusCode());

        assertEquals(replacement, row("u1").get("password"));
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }

    @Test
    void deletionAfterCredentialReadDoesNotReportSuccessfulPasswordChange() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        doAnswer(invocation -> {
            Object originalRows = invocation.callRealMethod();
            new JdbcTemplate(database).update("delete from user_account where id=?", "u1");
            return originalRows;
        }).when(jdbc).queryForList(eq("select password from user_account where id=? limit 1"), eq("u1"));

        assertEquals(HttpStatus.CONFLICT, auth.changePassword("u1",
            Map.of("oldPassword", "oldPass123", "newPassword", "newPass456")).getStatusCode());

        assertEquals(0, jdbc.queryForObject("select count(*) from user_account", Integer.class));
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"      ", "\t\n \t\n ", "\u2003\u2003\u2003\u2003\u2003\u2003"})
    void changeRejectsWhitespaceOnlyPasswordWithoutWriting(String password) {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        Map<String, Object> before = row("u1");

        assertEquals(HttpStatus.BAD_REQUEST, auth.changePassword("u1",
            Map.of("oldPassword", "oldPass123", "newPassword", password)).getStatusCode());

        assertEquals(before, row("u1"));
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"      ", "\t\n \t\n ", "\u2003\u2003\u2003\u2003\u2003\u2003"})
    void resetRejectsWhitespaceOnlyPasswordWithoutWritingOrCachedMutation(String password) {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        Map<String, Object> target = cachedResetTarget();
        Map<String, Object> cachedBefore = new LinkedHashMap<>(target);
        Map<String, Object> before = row("u1");

        assertEquals(HttpStatus.BAD_REQUEST, auth.resetPassword("a1",
            Map.of("userId", "u1", "newPassword", password)).getStatusCode());

        assertEquals(before, row("u1"));
        assertEquals(cachedBefore, target);
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }


    @Test
    void changeRequiresExactlyOneAffectedRowBeforeReportingSuccess() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        Map<String, Object> before = row("u1");
        // A unique-ID UPDATE cannot normally affect two rows; still fail closed at the boundary.
        doReturn(2).when(jdbc).update(anyString(), any(Object[].class));

        assertFalse(auth.changePassword("u1", Map.of("oldPassword", "oldPass123",
            "newPassword", "newPass456")).getStatusCode().is2xxSuccessful());

        assertEquals(before, row("u1"));
        verify(store, never()).invalidateCache();
        verifyNoInteractions(logs);
    }

    @Test
    void resetRequiresExactlyOneAffectedRowBeforeReportingSuccess() {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        Map<String, Object> target = cachedResetTarget();
        Map<String, Object> cachedBefore = new LinkedHashMap<>(target);
        Map<String, Object> before = row("u1");
        doReturn(2).when(jdbc).update(anyString(), any(Object[].class));

        assertFalse(auth.resetPassword("a1", Map.of("userId", "u1",
            "newPassword", "newPass456")).getStatusCode().is2xxSuccessful());

        assertEquals(before, row("u1"));
        assertEquals(cachedBefore, target);
        verify(store, never()).invalidateCache();
        verify(store, never()).saveRecord(anyString(), any());
        verifyNoInteractions(logs);
    }


    private static Stream<String> oversizedCredentials() {
        return Stream.of("a".repeat(73), "密".repeat(25));
    }

    private static Stream<String> safeCredentials() {
        return Stream.of("a".repeat(72), "密".repeat(24), "安全密码🙂abc", "abcdef");
    }

    private void assertByteLimitRecovery(org.springframework.http.ResponseEntity<?> response) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        String message = String.valueOf(body.get("message"));
        assertTrue(message.contains("72"), message);
        assertTrue(message.contains("UTF-8"), message);
        assertTrue(message.toLowerCase().contains("reset"), message);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(store, never()).saveRecord(anyString(), any());
        verify(store, never()).invalidateCache();
        verifyNoInteractions(logs, sessions);
    }

    @ParameterizedTest
    @MethodSource("oversizedCredentials")
    void hashHelperRejectsOversizedUtf8BeforeProducingUnsafeHash(String raw) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> auth.hashPassword(raw));
        assertTrue(error.getMessage().contains("72"));
        assertTrue(error.getMessage().contains("UTF-8"));
        assertTrue(error.getMessage().toLowerCase().contains("reset"));
        verifyNoInteractions(store, logs, sessions);
    }

    @ParameterizedTest
    @MethodSource("oversizedCredentials")
    void matchesRejectsOversizedOriginalAndDistinctSuffixCollision(String raw) {
        // Construct a synthetic pre-fix credential with the real encoder, not our guarded helper.
        String unsafeHash = new BCryptPasswordEncoder().encode(raw);
        String collision = raw.substring(0, raw.length() - 1) + "x";
        assertTrue(new BCryptPasswordEncoder().matches(collision, unsafeHash),
            "Fixture must reproduce the installed encoder's first-72-byte collision");

        assertFalse(auth.matchesPassword(raw, unsafeHash));
        assertFalse(auth.matchesPassword(collision, unsafeHash));
        assertFalse(auth.matchesPassword(raw, raw), "Oversized plaintext legacy credentials also fail closed");
        verifyNoInteractions(store, logs, sessions);
    }

    @ParameterizedTest
    @MethodSource("oversizedCredentials")
    void loginRejectsOversizedPlaintextLegacyInsteadOfSilentlyUpgrading(String raw) {
        insertUser("u1", "student1", raw);
        Map<String, Object> before = row("u1");
        clearInvocations(jdbc);

        assertByteLimitRecovery(auth.login(Map.of("username", "student1", "password", raw)));

        verifyNoInteractions(jdbc, store);
        assertEquals(before, row("u1"));
    }

    @ParameterizedTest
    @MethodSource("oversizedCredentials")
    void loginRejectsDistinctOversizedSuffixAgainstLegacyTruncatedHash(String raw) {
        String unsafeHash = new BCryptPasswordEncoder().encode(raw);
        String collision = raw.substring(0, raw.length() - 1) + "x";
        insertUser("u1", "student1", unsafeHash);
        Map<String, Object> before = row("u1");
        clearInvocations(jdbc);

        assertByteLimitRecovery(auth.login(Map.of("username", "student1", "password", collision)));

        verifyNoInteractions(jdbc, store);
        assertEquals(before, row("u1"));
    }

    @ParameterizedTest
    @MethodSource("oversizedCredentials")
    void changeRejectsOversizedNewCredentialWithoutWriteAuditOrSession(String raw) {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        Map<String, Object> before = row("u1");
        clearInvocations(jdbc);

        assertByteLimitRecovery(auth.changePassword("u1",
            Map.of("oldPassword", "oldPass123", "newPassword", raw)));

        assertEquals(before, row("u1"));
    }

    @ParameterizedTest
    @MethodSource("oversizedCredentials")
    void resetRejectsOversizedNewCredentialWithoutWriteAuditSessionOrCachedMutation(String raw) {
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        Map<String, Object> target = cachedResetTarget();
        Map<String, Object> cachedBefore = new LinkedHashMap<>(target);
        Map<String, Object> before = row("u1");
        clearInvocations(jdbc);

        assertByteLimitRecovery(auth.resetPassword("a1", Map.of("userId", "u1", "newPassword", raw)));

        assertEquals(before, row("u1"));
        assertEquals(cachedBefore, target);
    }

    @ParameterizedTest
    @MethodSource("oversizedCredentials")
    void changeRejectsOversizedOldCredentialWithExplicitResetRecovery(String raw) {
        insertUser("u1", "student1", new BCryptPasswordEncoder().encode(raw));
        cachedUser();
        Map<String, Object> before = row("u1");
        clearInvocations(jdbc);

        assertByteLimitRecovery(auth.changePassword("u1",
            Map.of("oldPassword", raw, "newPassword", "newPass456")));

        verifyNoInteractions(jdbc);
        assertEquals(before, row("u1"));
    }

    @ParameterizedTest
    @MethodSource("safeCredentials")
    void byteLimitAndNormalUnicodeCredentialsStillHashMatchChangeResetAndLogin(String raw) {
        String hash = auth.hashPassword(raw);
        assertTrue(auth.matchesPassword(raw, hash));
        assertFalse(auth.matchesPassword("incorrect", hash));
        assertFalse(auth.matchesPassword(raw + "x", hash), "Appending a suffix must not preserve authentication");
        insertUser("u1", "student1", auth.hashPassword("oldPass123"));
        cachedUser();
        assertEquals(HttpStatus.OK, auth.changePassword("u1",
            Map.of("oldPassword", "oldPass123", "newPassword", raw)).getStatusCode());
        assertTrue(auth.matchesPassword(raw, (String) row("u1").get("password")));
        cachedResetTarget();
        assertEquals(HttpStatus.OK, auth.resetPassword("a1",
            Map.of("userId", "u1", "newPassword", raw)).getStatusCode());
        assertTrue(auth.matchesPassword(raw, (String) row("u1").get("password")));
        var response = auth.login(Map.of("username", "student1", "password", raw));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertTrue(sessions.validate((String) body.get("sessionToken"), "u1"));
        assertFalse(((Map<?, ?>) body.get("user")).containsKey("password"));
        // Safe legacy plaintext, including the exact byte boundary, still upgrades normally.
        jdbc.update("update user_account set password=? where id=?", raw, "u1");
        assertEquals(HttpStatus.OK, auth.login(
            Map.of("username", "student1", "password", raw)).getStatusCode());
        String upgraded = (String) row("u1").get("password");
        assertFalse(auth.needsPasswordUpgrade(upgraded));
        assertTrue(auth.matchesPassword(raw, upgraded));
    }


    private void interleaveAfterLegacyLoginRead(Runnable concurrentWrite) {
        doAnswer(invocation -> {
            Object originalRows = invocation.callRealMethod();
            concurrentWrite.run();
            return originalRows;
        }).when(jdbc).queryForList(eq(
            "select id,role,username,password,name,department_id,class_id,major from user_account where username=? limit 1"),
            eq("student1"));
    }

    @Test
    void legacyLoginUpgradeCannotOverwriteConcurrentCredentialReplacementOrIssueSession() {
        insertUser("u1", "student1", "oldPass123");
        String replacement = auth.hashPassword("concurrentReset456");
        interleaveAfterLegacyLoginRead(() -> new JdbcTemplate(database).update(
            "update user_account set password=?,name=? where id=?", replacement, "Concurrent Name", "u1"));

        var response = auth.login(Map.of("username", "student1", "password", "oldPass123"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(replacement, row("u1").get("password"));
        assertEquals("Concurrent Name", row("u1").get("name"));
        assertFalse(((Map<?, ?>) response.getBody()).containsKey("sessionToken"));
        verifyNoInteractions(store, logs, sessions);
    }

    @Test
    void legacyLoginUpgradeOfConcurrentlyDeletedAccountCannotAuditOrIssueSession() {
        insertUser("u1", "student1", "oldPass123");
        interleaveAfterLegacyLoginRead(() -> new JdbcTemplate(database).update(
            "delete from user_account where id=?", "u1"));

        var response = auth.login(Map.of("username", "student1", "password", "oldPass123"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_account", Integer.class));
        assertFalse(((Map<?, ?>) response.getBody()).containsKey("sessionToken"));
        verifyNoInteractions(store, logs, sessions);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void legacyLoginUpgradeUnexpectedRowCountFailsBeforeAuditOrSession(int count) {
        insertUser("u1", "student1", "oldPass123");
        Map<String, Object> before = row("u1");
        doReturn(count).when(jdbc).update(anyString(), any(Object[].class));

        var response = auth.login(Map.of("username", "student1", "password", "oldPass123"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(before, row("u1"));
        assertFalse(((Map<?, ?>) response.getBody()).containsKey("sessionToken"));
        verifyNoInteractions(store, logs, sessions);
    }

}
