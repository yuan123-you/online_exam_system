# Online exam backend and AI remediation — first-pass implementation plan

> Execute locally task-by-task with test-driven-development and verification-before-completion. No delegation is authorized for this request.

**Goal:** Establish a repeatable backend baseline and repair the confirmed first-pass issue without changing frontend code.

**Architecture:** Preserve existing service interfaces and database contracts. Write a failing regression test before a minimal fix; broaden investigation separately rather than replacing the application.

**Tech Stack:** Java 17 / Spring Boot / JDBC / MySQL

**Spec:** User request on 2026-10-08: cover backend, database business logic and AI business; do not modify frontend.

## Global constraints
- Preserve all pre-existing uncommitted changes; no reset, overwrite, commit, push or deployment.
- No production database writes or migrations in this pass.
- Business-rule ambiguities must be confirmed with the user before implementing.
- Mock external services for regression tests; do not spend AI tokens or call production endpoints.
- Passing existing unit tests is not evidence that end-to-end business is correct.

### Task 1: Prevent AI-imported questions from overwriting existing records
Files: backend/src/main/java/com/onlineexam/service/AiService.java; backend/src/test/java/com/onlineexam/service/AiServiceImportTest.java.
Evidence: importQuestions accepts a request-supplied id; StoreService.upsertQuestion uses INSERT ... ON DUPLICATE KEY UPDATE, including teacher_id. An import is creation, not an update.
Contract: every imported question receives a fresh server-generated id, including duplicate ids in the same request and ids belonging to soft-deleted questions. Preserve response fields, per-item errors and role checks.
- [x] Add service tests for another teacher's id, same-teacher id, soft-deleted id, repeated id and normal generated/import ids.
- [x] Verify tests fail against the existing service.
- [x] Generate every new question id on the server; never use request ids as database keys.
- [x] Run targeted tests and full backend Maven tests.

### Follow-up investigation (not yet diagnosed or implemented)
Verify AI question type/answer validation, grading timeout/concurrent mutation, rate limits, streaming cancellation, exam submission idempotency, score calculation and paper/exam ownership. Database schema changes require separate migration and rollback review.

## Execution evidence
- JDK 21 selected per pom.xml; system default JDK 17 cannot compile release 21. No project Java-version downgrade was made.
- New import regression: before fix 4 failed / 2 passed; after fix 6 passed.
- Baseline suite: 274 tests, 9 failures / 6 errors. Final suite: 280 tests, the same 9 failures / 6 errors. Global regression is NOT green.
- Existing failure groups: AuthServiceTest (5), EntityCrudServiceTest (4), AnalysisServiceTest (6 unused-stubbing errors); root causes remain to be diagnosed.
- No database schema migration or live database writes were performed.
