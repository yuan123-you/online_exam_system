# Auth regression contracts — 2026-10-08

## Current status

Authorized password-write integrity, BCrypt byte-limit, and legacy-login upgrade CAS fixes are now in AuthService. Final selected auth/registration verification is 73 passing tests, including all 39 persistence contracts; combined verification has 74 tests and only the unchanged empty-reset/default-password assertion fails. All focused Maven commands are terminal. Historical pass findings below are retained as investigation evidence; later authorized continuations supersede the earlier deferred findings.

## First pass: scope and decisions

Bounded worker edits only `backend/src/test/java/com/onlineexam/service/AuthServiceTest.java`, the focused new `backend/src/test/java/com/onlineexam/service/AuthServicePersistenceContractTest.java`, and this document. No production edits were necessary for the five obsolete-boundary failures. Existing dirty AuthService/session/registration changes remain untouched. No StoreService/submission/frontend/config edits, live database access, deployment, or Git history operations.

## Reproduction and root cause

The initial offline Maven run reproduced exactly 25 tests / 6 failures / 0 errors.

| Original failure | Actual cause | Action |
| --- | --- | --- |
| ChangePassword validInput | Fixture put password into Store, but production reads it from JDBC; Mockito returned an empty query result and service returned 401 before checking the input. Its write assertion also expected obsolete saveRecord. | Make cached user password-free, stub exact password SELECT, verify password-only JDBC update, BCrypt match, invalidation, no saveRecord/cache password. Preserve expected 200. |
| ChangePassword wrongOldPassword | Same missing JDBC credential stub. | Supply DB credential; preserve expected 400 and verify no writes/invalidation/audit. |
| ChangePassword newPasswordTooShort | Same missing JDBC credential stub. | Supply DB credential; preserve expected 400 and verify no writes/invalidation/audit. |
| ChangePassword newPasswordTooLong | Same missing JDBC credential stub. | Supply DB credential; preserve expected 400 and verify no writes/invalidation/audit. |
| Login plainTextPassword_autoUpgradesToBCrypt | Login already updates the password column directly, but test verifies StoreService.saveRecord. | Verify exact JDBC UPDATE, BCrypt hash matching original plaintext, and no StoreService interaction. Preserve expected 200 and upgrade behavior. |
| ResetPassword blankNewPassword_usesDefault123456 | Existing dirty production change removed blank-to-123456 default, while existing test still requires it. Not a mock-boundary failure. | Leave test and production unchanged; policy decision required. |

StoreService.loadStoreFromDb explicitly excludes password from its user SELECT. AuthService.changePassword queries `select password from user_account where id=? limit 1` and updates only that column; login's legacy-password upgrade likewise updates directly. Restoring a cached-password fallback or a whole-user write would regress the actual boundaries.

## Explicit SQL contracts

Five additional tests use real JdbcTemplate, BCrypt, and isolated UUID-named in-memory H2 databases (SingleConnectionDataSource, closed after each test). Only StoreService and the audit sink are mocked. No Spring context/application datasource is loaded.

1. Plaintext login persists a matching BCrypt hash only for the target account; all profile columns and another account remain unchanged; returned user omits password and normalizes organization keys; issued session validates for the target.
2. Existing BCrypt login leaves persisted credential unchanged (no gratuitous rehash).
3. Password change succeeds with a password-free cached identity and DB-held credential, preserves profile/other account, does not mutate cached user, invalidates the cache, and logs success.
4. Wrong old password leaves the row intact and causes no cache invalidation, whole-user write, or success audit.
5. An account deleted from the DB cannot change a password via a stale cached identity.

These are additional contract tests for already-existing implementation, not claimed as red-first implementation fixes. No implementation fix was introduced.

## Verification evidence

Use `JAVA_HOME=C:\Users\游源\.jdks\temurin-21`; prepend `%JAVA_HOME%\bin` to PATH. All runs were offline and sequential, with no full-suite run.

From `D:\Codex Web\online_system\backend`:

```powershell
mvn -o '-Dtest=AuthServiceTest' '-Dsurefire.reportsDirectory=target/auth-contract-red' test
mvn -o '-Dtest=AuthServiceTest,AuthServicePersistenceContractTest,AuthServiceRegistrationTest' test
mvn -o '-Dtest=AuthServiceTest$LoginTests,AuthServiceTest$ChangePasswordTests,AuthServiceTest$PasswordUtilTests,AuthServicePersistenceContractTest,AuthServiceRegistrationTest' test
```

- Red: `backend/target/auth-contract-red.log`: 25 tests, 6 failures, 0 errors.
- After fixture repair plus SQL contracts: `backend/target/auth-contract-after.log`: 40 tests, 1 failure, 0 errors (39 pass). Sole failure is unchanged blank-reset default expectation.
- Green subset: `backend/target/auth-contract-green.log`: 36 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. It deliberately excludes all four ResetPassword tests and verifies the repaired boundaries, password utilities, SQL contracts, and existing registration tests.
- Important: the attempted `surefire.reportsDirectory` command-line property did not redirect XML reports with this Maven configuration. Stable worker-owned evidence is the three `auth-contract-*.log` files; Surefire XML used the default `backend/target/surefire-reports`. Parent should run its final suite after this worker's runs end.

No failure was deleted, disabled, or converted to an opposite status assertion. Overall auth verification intentionally remains red until the reset policy conflict is resolved.

## Parent decision required / new findings

1. **Blank reset policy:** Confirm whether admins must provide an explicit new password (current dirty implementation) or whether a separately specified secure reset mechanism is intended. The old shared default must not be restored merely to satisfy the stale test. Once explicit-password behavior is authorized, replace that old default expectation with rejection plus no-write assertions; worker intentionally did not decide this.
2. **Password policy differs across endpoints:** registration enforces 8–64 characters and 72 UTF-8 bytes for explicit passwords, while change/reset validate only 6–100 characters. This requires a policy decision; worker did not impose registration's policy elsewhere. The new contracts use ordinary ASCII credentials well inside existing limits.
3. **Whitespace-only inputs need policy review:** change/reset can pass their length checks for six or more spaces, while matchesPassword explicitly refuses blank raw passwords. Such a successful write can create a credential that login cannot accept. This source-level inconsistency is not fixed without an agreed password-input contract.
4. **Reset still uses cached-row mutation + whole-user save:** unlike login/change it puts password into the cached target map and calls saveRecord, whose current implementation performs a user upsert. This is a separate cache/persistence concern (including stale-target handling) for parent follow-up; this worker did not refactor it or alter StoreService.


## Authorized continuation: password-write integrity fixes

The parent explicitly authorized these fixes after the first pass. This section supersedes the first-pass statement that no production edits were made and resolves first-pass findings 3 and 4. The empty-reset/default-password decision and its old failing assertion remain untouched.

### Red-first regressions

Before production edits, added deterministic real-H2 tests for reset cache/profile preservation, stale cached deleted targets, competing password replacement after credential read, deletion after credential read, and whitespace-only passwords (spaces, tabs/newlines, and Unicode em spaces). A JdbcTemplate spy interleaves a real SQL competing write immediately after reading the old credential; no sleeps or nondeterministic thread timing are needed.

- `backend/target/auth-contract-write-red.log`: 15 tests, 10 failures, 0 errors. Existing five SQL contracts pass. The ten new failing cases show false 200 responses or cached-row password mutation.
- Two additional boundary tests inject an unexpected update count of 2, verifying that success requires exactly one affected row.
- `backend/target/auth-contract-rowcount-red.log`: 2 tests, 2 failures, 0 errors. Both methods originally reported success.

### Minimal production changes

Only `backend/src/main/java/com/onlineexam/service/AuthService.java` was changed:

- `changePassword`: retain old-password authentication and min6/max100 checks; reject all-whitespace new credentials; update using `where id=? and password=?` against the stored credential used for old-password proof. Zero affected rows returns 409 (account/password changed), unexpected non-one count returns 500. Only one-row success invalidates cache and audits.
- `resetPassword`: retain admin/target checks, min6/max100, and existing empty-input rejection; reject all-whitespace new credentials; perform password-only JDBC UPDATE by target ID. Zero affected rows returns 404, unexpected non-one count returns 500. Only one-row success invalidates cache and audits. Never mutate the shared cached user or upsert stale profile data.
- Existing valid change/custom-reset unit fixtures now explicitly return an update count of one and verify the new SQL boundary. The blank-reset old test remains unchanged.
- No defaults, trimming, registration limits, session revocation behavior, or unrelated source files changed.

### Post-fix evidence

Using the same Java 21 runtime and offline Maven:

```powershell
mvn -o '-Dtest=AuthServicePersistenceContractTest,AuthServiceTest,AuthServiceRegistrationTest' test
mvn -o '-Dtest=AuthServicePersistenceContractTest,AuthServiceRegistrationTest,AuthServiceTest$LoginTests,AuthServiceTest$ChangePasswordTests,AuthServiceTest$PasswordUtilTests,AuthServiceTest$ResetPasswordTests#resetPassword_adminResetsWithCustomPassword_returnsOk+resetPassword_nonAdmin_returnsForbidden+resetPassword_targetUserNotFound_returnsNotFound' test
```

- `backend/target/auth-contract-write-after.log`: 52 tests, 1 failure, 0 errors, 0 skipped. Sole failure remains `resetPassword_blankNewPassword_usesDefault123456` (old expectation 200 versus current 400).
- `backend/target/auth-contract-write-green.log`: 51 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. This selection includes the three non-conflicting ResetPassword tests as well as all 17 SQL/write-integrity contracts, and excludes only the unresolved empty-reset assertion.
- No full-suite worker run. Parent can run exam/full acceptance now; all focused Maven runs have ended.

### Separately verified BCrypt byte-limit concern (not fixed)

A JShell probe against the project's installed spring-security-crypto 6.3.4 and spring-jcl 6.1.14, using only synthetic credentials, confirmed:

- 73 ASCII characters / 73 UTF-8 bytes: encoding succeeds, original matches, and a different last character also matches the same hash.
- 25 repeated Chinese characters / 75 UTF-8 bytes: encoding succeeds, original matches, and replacing the final character with ASCII `x` also matches the same hash (both share the first 72 bytes).

Evidence: `backend/target/auth-contract-bcrypt-byte-probe.log`. JShell emitted environment startup noise from an unrelated installed `test.exe`, but evaluated the probe and printed both results. No credentials, application datasource, or network service were used.

Thus change/reset's existing character-only limits permit effective BCrypt truncation, unlike registration's existing explicit 72-byte validation. Separate handling must determine validation/compatibility and legacy-login migration consequences before modifying password byte policy. This worker did not add a byte limit or alter registration/login behavior. The empty-reset policy still needs the user's decision.


## Authorized continuation: reject BCrypt UTF-8 truncation

The parent authorized enforcing the encoder's technical 72-byte bound, including legacy authentication and the public common hash helper. This is not a new eight-character policy, migration, registration-default change, or session policy.

### Red evidence before source changes

Added seven two-case regression groups for synthetic 73-byte ASCII and 75-byte multibyte inputs, plus four positive cases. Distinct-suffix cases explicitly recreate the installed encoder's collision before checking that AuthService rejects it. The direct encoder is used only to construct synthetic pre-fix legacy fixtures; production always uses the guarded helper.

`backend/target/auth-contract-byte-red.log`: 35 persistence-contract cases, 14 failures, 0 errors. Failures show oversized new hashes accepted, oversized helper inputs not throwing, raw/colliding BCrypt authentication accepted, oversized plaintext silently upgraded, and oversized old-password proofs accepted. The 17 earlier contracts and four positive byte-boundary/Unicode/six-character cases pass on the old source.

### Minimal source changes

Only AuthService production code was edited in this continuation:

- A shared UTF-8 byte-count predicate checks `>72`, not character length.
- `hashPassword` throws IllegalArgumentException before the encoder for oversized input, protecting callers such as generic admin credential writes from creating newly truncated hashes. Callers can surface this as input validation; no generic CRUD sources were edited here.
- `matchesPassword` returns false for oversized raw input before BCrypt or plaintext comparison.
- `login` returns 400 with explicit byte-limit and admin-reset recovery guidance before any database lookup, write, audit, or session issuance. An oversized legacy plaintext credential cannot be silently upgraded, and an oversized suffix collision cannot authenticate.
- `changePassword` returns the same explicit error for oversized old-password proof (before DB credential read) and oversized new credentials (before write).
- `resetPassword` rejects oversized new credentials before write. Password-free cache maps remain unchanged.
- Preserve existing min6/max100 character checks, all-whitespace rejection, public helper's existing behavior for ordinary shorter input, registration's existing defaults/limits, and session behavior. The empty-reset conflict remains unchanged.

Recovery message:

> Password must not exceed 72 UTF-8 bytes. Oversized legacy passwords cannot be safely authenticated; ask an administrator to reset the password.

No actual credentials were inspected. No automatic reset, truncation, unsafe upgrade, live database access, or migration was performed. Historical BCrypt hashes do not record the original raw byte count; these guards prevent oversized inputs and newly unsafe hashes, but do not identify or retroactively rewrite historical truncated hashes. Exact-72-byte inputs remain valid as requested.

### Coverage and terminal verification

The new rejection contracts verify database contents unchanged, no JDBC writes, no StoreService saveRecord/invalidation, no success audit, no session activity, and recovery messages containing 72, UTF-8, and reset guidance. Login and oversized-old-password validation additionally verify no JDBC work at all after fixture setup.

Safe-input positives cover 72 ASCII bytes, 24 Chinese characters totaling 72 UTF-8 bytes, normal mixed Unicode/emoji, and a six-character ASCII password. Each hashes/matches, rejects a changed/appended suffix, changes password, resets password, logs in with a valid session, and safely upgrades legacy plaintext. No eight-character minimum was introduced.

```powershell
mvn -o '-Dtest=AuthServicePersistenceContractTest' test
mvn -o '-Dtest=AuthServicePersistenceContractTest,AuthServiceTest,AuthServiceRegistrationTest' test
mvn -o '-Dtest=AuthServicePersistenceContractTest,AuthServiceRegistrationTest,AuthServiceTest$LoginTests,AuthServiceTest$ChangePasswordTests,AuthServiceTest$PasswordUtilTests,AuthServiceTest$ResetPasswordTests#resetPassword_adminResetsWithCustomPassword_returnsOk+resetPassword_nonAdmin_returnsForbidden+resetPassword_targetUserNotFound_returnsNotFound' test
```

All used the specified Java 21 runtime and offline Maven, run sequentially:

- `backend/target/auth-contract-byte-after.log`: 70 tests, 1 failure, 0 errors, 0 skipped. Sole failure is unchanged `resetPassword_blankNewPassword_usesDefault123456`.
- `backend/target/auth-contract-byte-green.log`: 69 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS. All 35 persistence/write/byte contracts, all registration tests, and all non-conflicting original auth tests pass. The final positive cases also explicitly cover safe legacy plaintext upgrades.
- Every focused command has ended; no full suite was run by this worker. Parent can now run final acceptance.

This continuation edited AuthService.java, AuthServicePersistenceContractTest.java, and this plan only. Earlier scoped AuthServiceTest.java fixture changes remain. Parent's concurrent Store/Entity CRUD/user-profile changes were not edited.


## Final bounded continuation: legacy-login upgrade compare-and-set

Parent source inspection identified the remaining ID-only, unchecked legacy-upgrade UPDATE in login. This continuation changes only that upgrade branch, its successful original unit fixture, focused regressions, and this evidence document. No new authentication/session/default-password policies or other source files were introduced.

### Red-first reproduction

Four additional cases use the same isolated H2/JdbcTemplate fixture:

1. After the real login SELECT reads a plaintext credential, a deterministic interleaved SQL writer replaces the password and updates the profile name. Login must return 409, preserve the replacement credential/profile, and produce no success audit or session.
2. After login SELECT, an interleaved SQL writer deletes the account. Login must return 409, leave the account deleted, and produce no audit/session.
3. Inject an unexpected affected-row count of -1: explicit 500 failure, unchanged database contents, no audit/session.
4. Inject an unexpected affected-row count of 2: the same fail-closed behavior.

`backend/target/auth-contract-login-cas-red.log`: 39 persistence-contract cases, 4 failures, 0 errors. All four new cases returned 200 on the old source; the earlier 35 contracts passed.

### Minimal fix and unchanged behavior

Login captures the same stored plaintext credential it verified and upgrades using:

```sql
update user_account set password=? where id=? and password=?
```

A zero-row result returns 409 with retry-login guidance. Any unexpected non-one count returns 500 with explicit upgrade-failure guidance. Exactly one affected row is required before success audit/session issuance or clearing failed attempts. Failed-attempt clearing was moved below the successful upgrade check, preserving the existing successful-login behavior without treating a failed upgrade as success.

The successful plaintext-upgrade Mockito fixture now returns one for the guarded UPDATE and verifies the original stored credential as the guard argument plus a BCrypt value matching the original plaintext. Existing real-SQL contracts continue to verify profile/other-account preservation, sanitized response fields, and session validity. Existing BCrypt logins do not enter the upgrade branch and are unchanged. No session revocation policy was added.

### Terminal verification evidence

All commands used the specified Java 21 runtime, offline Maven, and focused selections only:

```powershell
mvn -o '-Dtest=AuthServicePersistenceContractTest' test
mvn -o '-Dtest=AuthServicePersistenceContractTest,AuthServiceTest,AuthServiceRegistrationTest' test
mvn -o '-Dtest=AuthServicePersistenceContractTest,AuthServiceRegistrationTest,AuthServiceTest$LoginTests,AuthServiceTest$ChangePasswordTests,AuthServiceTest$PasswordUtilTests,AuthServiceTest$ResetPasswordTests#resetPassword_adminResetsWithCustomPassword_returnsOk+resetPassword_nonAdmin_returnsForbidden+resetPassword_targetUserNotFound_returnsNotFound' test
```

- `backend/target/auth-contract-login-cas-after.log`: 74 tests, 1 failure, 0 errors, 0 skipped. Sole failure remains the unchanged old empty-reset/default-password assertion.
- `backend/target/auth-contract-login-cas-green.log`: 73 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS. All 39 persistence contracts pass.
- All worker commands are terminal. No full suite was run; parent can rerun final acceptance now.

Exact edited paths in this continuation: AuthService.java, service/AuthServiceTest.java (successful plaintext-login method only), service/AuthServicePersistenceContractTest.java, and this plan. Earlier changes and the remaining empty-reset policy conflict remain intact. Parent's reported full-suite soft-delete assertion failures were not edited or run by this worker.
