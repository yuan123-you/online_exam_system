# Published exam immutable content implementation plan

> Execute locally with test-driven-development and verification-before-completion. Implementation remains local; read-only review follows the requesting-code-review skill. No commits, pushes or deployments.

**Goal:** Freeze question stems, options, answers, scores and paper configuration on first publication; later bank/paper edits must not change delivery, deadlines or scoring.
**Architecture:** Store a separate server-owned exam_snapshot JSON document keyed by exam ID. Capture in the same database transaction as publication using fresh locked paper/question rows. Resolve published exam content through one module, never through mutable bank data. Existing response shapes remain compatible; student responses never include the private snapshot.
**Tech Stack:** Java 21, Spring Boot/JDBC, MySQL; JUnit/Mockito and isolated H2 transaction tests.
**Spec:** User confirmation on 2026-10-08; D:/HBuilderPrograms/blo/docs/2026-10-08-business-refactoring-scope.md.

## Global constraints
- Frontend unchanged; preserve pre-existing uncommitted modifications, especially schema.sql.
- Snapshot captured at first publication; unpublish/republish must not rewrite it. Reject swapping paperId after freezing.
- Snapshots are server-owned and stored separately from API exam maps to prevent answer leakage.
- Drafts continue using live bank data. Published exams without a recoverable snapshot fail closed for content delivery and automatic grading; saved scores/details remain visible with missing-version metadata.
- Do not manufacture historical publication versions from current questions. No automatic historical backfill, migration execution or real AI calls.
- New schema is additive CREATE TABLE IF NOT EXISTS; document deployment gate, backup/recovery and rollback limitations.

### Task 1: Regression loop for frozen delivery, grading and persistence
Files: backend/src/test/java/com/onlineexam/service/PublishedExamSnapshotTest.java; backend/src/test/java/com/onlineexam/ExamSnapshotTransactionTest.java; backend/pom.xml (test-only H2).
- [x] Write tests through existing StoreService / ExamService / SubmissionService interfaces with changed live bank data and persisted snapshot fixtures.
- [x] Run tests against original implementation and observe actual mismatches, not merely missing classes.
- [x] Cover student answer redaction, paper settings, deleted live questions, repeated publication, invalid references and missing historical snapshots.

### Task 2: Transactional snapshot publication
Files: backend/src/main/java/com/onlineexam/StoreService.java; backend/src/main/java/com/onlineexam/service/ExamContent.java; backend/src/main/resources/schema.sql.
- [x] Add exam_snapshot(exam_id PK/FK, content_json JSON, created_at), no UPDATE/upsert of snapshot contents.
- [x] Load private snapshot data into Store separately; malformed/missing required data must not silently fall back to the bank.
- [x] On first publication, lock exam (when present), paper and referenced questions, capture detached nested values, persist exam and snapshot atomically.
- [x] Existing frozen snapshots are preserved; incomplete publication fails without published records.
- [x] Use an isolated database test to verify rollback and restart/reload persistence; no live datasource.

### Task 3: Consumer integration and historical behavior
Files: backend/src/main/java/com/onlineexam/service/ExamService.java; backend/src/main/java/com/onlineexam/service/SubmissionService.java; backend/src/main/java/com/onlineexam/service/EntityCrudService.java; backend/src/main/java/com/onlineexam/controller/SubmissionController.java; backend/src/main/java/com/onlineexam/config/GlobalExceptionHandler.java; relevant existing tests.
- [x] Use frozen paper/questions for details, option randomization, deadlines, grade totals and review metadata.
- [x] Keep missing-version metadata display safe; reject new automatic grading of legacy missing snapshots.
- [x] Adapt only valid published-test fixtures to include versions; retain explicit missing-snapshot regression coverage.
- [x] Run targeted suites, full Maven suite and diff checks. Report baseline failures separately, never claim global success if failures remain.
- [x] Document deployment preconditions and unresolved historical recovery; no database changes are applied by this coding session.

## Execution / deviations
- The existing SubmissionController and EntityCrudService required no changes: their existing calls already cross the modified StoreService and ExamService entry points. Their proposed plan write scope was not used.
- Integration expanded to ClassAnalysisController, AiService grading and ExcelExportService after regression tests exposed missed consumers and unknown pass-threshold interpretation.
- Existing ExamServiceTest, SubmissionServiceTest and the success fixtures in AnalysisServiceTest now explicitly model valid published versions rather than silently relying on live paper data; no assertions were weakened or tests disabled.
- Isolated H2 transaction tests include publication rollback, restart/reload, duplicate concurrent publication and preservation across withdrawal/republication. H2 does not replace target MySQL integration.
- Original regression: 12 tests, 9 failures / 1 error. Original transaction regression: 3 tests, 2 failures / 1 error. Cache-completion regression initially failed. Review regressions initially had 4 failures / 1 error, then were repaired.
- Final focused acceptance: 98 tests passed, including 30 newly added snapshot tests.
- New tests total 30. Full final regression: 310 tests, 9 failures / 6 errors; failure identities match the pre-change baseline. Full suite is NOT green.
- Read-only review findings were reproduced and addressed; follow-up static review reported no remaining important findings in its bounded scope.

## Still required before production acceptance
- [ ] Resolve the existing 9 failures and 6 test errors before claiming full-project verification.
- [ ] Validate the additive DDL, locking and JSON round-trip on an isolated MySQL 8 instance.
- [ ] Inventory all legacy publications, including withdrawn exams without surviving submissions, and agree trustworthy recovery sources.
- [ ] Complete real end-to-end acceptance using isolated accounts/data; no production migration or deployment was performed.
