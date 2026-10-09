# Grading result integrity implementation plan

**Goal:** Failed or malformed AI grading cannot become a successful numerical recommendation, and unsubmitted sessions cannot be graded manually or by AI.
**Architecture:** Retain existing REST success contracts. Validate AI score/comment before declaring success; remove keyword fallback from the AI grading path. Parallel tasks return isolated results, assembled only after all complete. Validate submitted state and frozen exam availability before either new grading operation; manual writes use a detached candidate.
**Tech Stack:** Java 21, Spring, JUnit/Mockito; no live resources.
**Spec:** User requests continuing backend/database/AI business remediation; no frontend changes. Confirmed source defects: manual grading has no state/snapshot gate; AI transport/parser failure yields keyword score and success, parsed missing score defaults to zero, timed-out tasks share mutable result maps/aggregate.

## Constraints
- Preserve unrelated dirty work. No commit, deployment, real AI or business database calls.
- Preserve partial manual score updates, regrading of completed submissions and objective-score editing for now; these policies are not redesigned without user confirmation.
- Keep integer grading model and existing successful response fields. Invalid AI output is not clamped/coerced into a score.
- AI suggestions never save final grades. A failed request returns existing error response, without partial numerical recommendation.
- No promise that cancellation interrupts a running provider call; late task results must not mutate returned responses or stored submission details.
- Broader submission CAS/session uniqueness and shared Store caching are separate unresolved work, not claimed fixed here.

## Task 1: AI recommendation truthfulness
Files: backend/src/main/java/com/onlineexam/service/AiService.java; backend/src/test/java/com/onlineexam/service/AiGradingIntegrityTest.java.
- [x] Run full baseline; add failing regressions for provider failure, invalid root/score/comment, later circuit rejection, executor rejection/interruption, missing submitted status, and valid/blank answers.
- [x] Remove keyword grading fallback; validate integer score in [0, fullScore] and nonblank string comment; record circuit success only after validation and evict invalid raw cached response.
- [x] Return isolated future results; on failure/timeout/interruption cancel best effort and return unavailable; preserve interrupt flag; aggregate only completed results in original question order.
- [x] Verify fake-provider tests and related AI/snapshot tests.

## Task 2: Manual-grade prerequisite and mutation integrity
Files: backend/src/main/java/com/onlineexam/controller/SubmissionController.java; backend/src/test/java/com/onlineexam/controller/ManualGradingIntegrityTest.java.
- [x] Red tests: unsubmitted sessions rejected, unknown historical version fails closed, failed save cannot corrupt the shared submission map.
- [x] Gate status and frozen version, then write detached submission data. Keep existing manual scoring policy pending dedicated decisions.
- [x] Positive partial/regrade/access regressions; full suite and baseline failure comparison; scoped diff checks; record report and remaining boundaries.


## Integration regressions added before implementation
- Detached/manual and first student submission responses rank the just-saved grade against stale read-view rows: 4 tests, 3 failed before correction. Use a request-local overlay, preserving original tie ordering and without mutating cached rows.
- Static reviewer found valid-looking object inside array and rounded high-precision fractional scores accepted. Added regression cases for those, prose-wrapped JSON, trailing objects, duplicate score fields; retain valid Markdown JSON envelope.
- SubmissionService.buildSubmissionReview and SubmissionReviewFreshGradeTest are added to the bounded write/review scope.


## Final verification, 2026-10-08
- Full pre-change baseline: 365 tests, 10 failures, 6 errors, 0 skipped. Failure identities saved before changing implementation.
- Initial regressions: 34 tests / 28 failures; cache/admission regressions: 3 / 3 failures; request-local rank: 4 / 3 failures; strict parser: 5 / 5 failures.
- Final acceptance including published-version contract tests: 197 passing tests, 0 failures/errors/skips (20:57:08 +08:00).
- Final full suite: 410 tests, 10 failures, 6 errors, 0 skipped (20:57:52 +08:00). Exact failure/error identity comparison equals baseline; no new failure identities. Full suite remains red, not claimed passing.
- A prior full attempt exposed missing-version-vs-state error precedence; restored the existing snapshot error contract rather than weakening its regression.
- Bounded read-only reviewer reported two parser findings, reproduced and corrected; recheck reported no important concrete scoped findings.
- Scoped tracked-source diff check and new-file whitespace check passed. No schema change or migration required in this round.
- Timeout remains the existing 60-second wait; interruption/rejection/provider-failure regressions use fake providers/executors. Wall-clock 60-second timeout and real-provider/end-to-end acceptance were not exercised.
- Durable save/submit/manual-grade concurrency control, per-exam student session uniqueness and broader cache transactions are still unresolved; this round does not claim to solve them.
