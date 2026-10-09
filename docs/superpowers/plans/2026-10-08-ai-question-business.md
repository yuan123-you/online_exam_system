# AI question business validation implementation plan

> Implement locally with test-driven-development; read-only review via requesting-code-review. No frontend edits, commits, deployments or real AI calls.

**Goal:** Model output is an untrusted question candidate, not a valid question merely because JSON parsing succeeded. Validate before returning synchronous generation results or importing, and reconcile the choice answer encoding with student submissions.
**Architecture:** A focused AiQuestionPolicy normalizes known metadata/types and validates structure, positive integral score, options and reference answers. A small shared choice-answer resolver maps explicit option labels to the original option values; scoring uses original frozen option order, never randomized display positions. Preserve existing import response shape and per-item error behavior.
**Tech Stack:** Java 21, Jackson, existing Spring services and JUnit/Mockito.
**Spec:** Model prompt's six supported types and A/B/C/D answer labels; existing ExamSessionModal sends full option values; existing SubmissionService compares answers; user requires backend/AI business refactoring with frontend unchanged.

## Constraints
- Do not invent a fallback question from provider text, truncate away options, guess an answer from an English word, silently default explicit invalid scores, or trust model/client identity.
- Missing optional score retains the existing default of 5. Existing free-text answer semantics and role/quota limits are retained.
- Reject empty/duplicate choice options and invalid/duplicate/unmapped answers; single/judge must have one answer. Do not impose a new minimum of two correct answers for multiple questions without a confirmed business rule.
- Import a mixed batch with per-item failures, saving only valid records; malformed root payload produces a normal 400 rather than ClassCastException.
- Keep published snapshot contents and saved final grades unchanged. Only new comparisons interpret explicit labels against original options.
- Streaming preview currently emits provider content; this pass enforces its persistence boundary, not a complete streaming-job redesign or factual-quality guarantee.

### Task 1: Regression feedback
Files: backend/src/test/java/com/onlineexam/service/AiQuestionBusinessTest.java; AiServiceImportTest.java; SubmissionServiceTest.java.
- [x] Fake the existing callAiApi seam; verify malformed/empty model results are not successful questions.
- [x] Test unknown types, invalid options/answers/scores, malicious identity fields, mixed batches and malformed root entries through real import/generation services.
- [x] Reproduce choice-letter versus option-value scoring using frozen original options.
- [x] Run failures before modifying implementation.

### Task 2: Focused candidate policy and scoring adapter
Files: backend/src/main/java/com/onlineexam/service/AiQuestionPolicy.java; ChoiceAnswers.java; AiService.java (parser/import only); SubmissionService.java (choice comparison only).
- [x] Extract candidate normalization/validation, retain IDs and ownership generation on the server.
- [x] Replace placeholder fallbacks with explicit failure; valid array structure alone is insufficient.
- [x] Apply the same policy at AI import regardless of synchronous or streaming origin.
- [x] Map explicit labels to known option values, preserve exact text matching and reject ambiguous generated candidates. Never change frozen stored contents or saved grades.
- [x] Run focused tests and full regression; compare remaining baseline failure identities without disabling tests.

### Task 3: Preserve the generated explanation without leaking it during exams
Evidence: generated questions already have explanation, but AI import and the question SQL currently discard it. Persisting it is necessary for the existing generated-content contract, not a new frontend feature.
Files: backend/src/main/resources/schema.sql; StoreService.java; repository/QuestionRepository.java; service/ExamService.java (student redaction); db/migrations/2026-10-08-ai-question-explanation.sql; existing ExamSnapshotTransactionTest.java and new privacy regression.
- [x] Reproduce explanation loss through real isolated import/persistence/snapshot delivery.
- [x] Add a nullable explanation column and a one-time migration with an explicit deployment prerequisite; do not execute it on live data.
- [x] Preserve explanation in question rows and future frozen versions; redact it along with answers from student exam content.
- [x] Verify migration/schema and real database round trip in isolated tests; target MySQL deployment still requires separate validation.

## Compatibility refinement after tracing actual preview/practice callers
- Valid generation/practice previews keep their existing letter-answer protocol; only formal import normalizes reference answers to actual option values.
- Add regressions for all six existing types and duplicate option contents despite distinct A/B prefixes.
- Existing PracticeSessionService uses an unanchored letter regex and can mark unrelated words or two empty extracted-letter lists as correct. Its comparison will use the same explicit option resolver; preserve the user's existing ownership-SQL changes and do not restructure the practice lifecycle in this pass.

- Reproduce and repair exact-key cache poisoning: raw HTTP-200 model text is cached before question validation for 30 minutes. A failed structured generation/practice result must evict that prompt's raw cache entry so a user retry can reach the provider; general caching policy is not changed.

## Acceptance limits
- [ ] Target MySQL integration, real provider/SSE compatibility and end-to-end acceptance remain required before deployment.
- [ ] Remaining historical-data / general lifecycle issues are not declared solved by this scoped implementation.

## Final execution evidence
- Initial AI regressions: 26 tests failed against original implementation. Explanation persistence regression independently failed. Compatibility/practice regressions: 6 additional failures observed before repair. Exact-key cache retry regressions: 2 failures. Choice-encoding review regressions: 3 failures, plus 2 formal-roundtrip/empty-content failures observed before fixes.
- Final focused acceptance: 144 tests passed, 0 failures/errors. This pass adds 46 tests (44 AI business cases, 1 real persistence/snapshot case and 1 metadata migration contract).
- Final full suite: 356 tests, 9 failures and 6 errors. Failure identities exactly equal the 310-test prior baseline; no new failures or test disablement.
- Read-only review's two choice-encoding findings were reproduced, repaired and rechecked; exact-key cache eviction rechecked in both public structured flows.
