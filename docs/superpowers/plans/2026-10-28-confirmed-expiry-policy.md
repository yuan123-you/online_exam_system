# Confirmed Expiry Settlement Implementation Plan

**Goal:** Implement the user-confirmed rule: once the effective individual deadline is reached, grade only answers already saved on the server; reject late edits and honor valid teacher extensions.

**Architecture:** Use one shared expiry/settlement path for student requests, exam restoration and a small server-side scheduled sweep (so closing the browser cannot prevent settlement). Keep the existing persisted revision CAS. Add transactional timed student writes that lock the current row and recheck the deadline after lock acquisition, preventing a request delayed by another write from overwriting saved answers after expiry. Do not apply the student guard to authorized teacher grading/extension writes.

**Tech Stack:** Spring Boot / JDK 21 / JDBC transactions / JUnit + H2; Vue / TypeScript / Vitest.

**Spec:** User explicitly confirmed the expiry policy on 2026-10-28 after the prior review.

## Constraints

- Preserve earlier uncommitted fixes. No commit, push, deployment or production data access.
- No grace period, client timestamps or client answer payloads in expired settlement.
- Normal effective deadline may become earlier but never silently renew; a teacher extension is the only exception.
- Failed/stale CAS cannot emit success-side effects, override an extension or recreate a completed session.
- Missing/invalid deadlines and missing published versions fail closed instead of inventing scores.

## Task 1: Reproduce deadline violations

**Create:** `backend/src/test/java/com/onlineexam/SubmissionExpiryPolicyTest.java`.

- [x] Cover individual/global expiry, saved-answer-only grading, final-state idempotency, subjective pending grades, invalid deadlines, effective-end clamping, no late starts and teacher extension behavior.
- [x] Add real H2 row-lock races for both save and submit crossing the deadline.
- [x] Run before production changes and capture failures.

## Task 2: Implement durable guarded writes and shared settlement

**Modify:** `StoreService.java`, `ExamService.java`, `SubmissionService.java`, `SubmissionController.java`, `ExamController.java`; update the existing lifecycle mock assertion for the new guarded student-write API.

- [x] Add explicit transactional student-write / expired-settlement methods; lock existing rows, recheck server time, then retain existing revision CAS and cache invalidation.
- [x] Clamp ordinary persisted deadlines rather than recomputing a later deadline.
- [x] Centralize expiry detection and detached grading of saved answers; timestamp automatic submission at the effective deadline.
- [x] Student submit and exam restore settle expired sessions; late save rejects without modifying answers.
- [x] Separate timeout probes from intentional manual submission so a stale browser clock/deadline cannot end a still-valid teacher extension.

## Task 3: Settle sessions without the browser

**Create:** `service/ExpiredSubmissionScheduler.java` and scheduler regression tests; **modify:** `OnlineExamApplication.java`.

- [x] Run a fixed-delay sweep; use the same settlement/CAS path.
- [x] Isolate failed/conflicting records, skip finished/non-expired/invalid sessions and retry transient failures on the next sweep.
- [x] Test that one broken record does not prevent other expired sessions from settling.

## Task 4: Align the exam modal

**Modify:** `src/api/client.ts`, `src/components/student/ExamSessionModal.vue`; **create:** its adjacent regression test.

- [x] Automatic timeout calls identify themselves; refresh an authoritative active deadline instead of prematurely submitting an extension.
- [x] Freeze expired answer inputs and stop late autosaves; show the saved-answer-only rule and errors.
- [x] Preserve intentional manual submission and avoid duplicate terminal events.

## Task 5: Review and verify

- [x] Run complete backend/frontend suites, typecheck, Webhook/dependency/hygiene checks, isolated build and official npm audit.
- [x] Attempt independent read-only review while verification runs; both attempts failed at model capacity. Complete documented manual review instead and explicitly report that no independent verdict was obtained for this phase.
- [x] Update the existing review report and mark the confirmed-policy step complete after verification.

## Red/green evidence

- Initial expiry integration suite: 14 tests, 11 demonstrated assertion failures (including both real row-lock races).
- Initial modal suite: five failures; client serialization suite: one of two failed.
- Initial targeted backend policy/lifecycle/scheduler subset subsequently passed; modal/client targeted tests and typecheck passed.
- Added further coverage for background settlement using the most recent durable draft, missing published versions, saved switch counts, failed DB write/secondary effects, scheduling enablement and browser clock skew.
## Additional final-write cache-race reproducer

The first complete run passed 744 backend / 150 frontend tests. Manual final review then identified a concrete stale-schedule race: changing the global end in the database while the Store cache holds the previous end could either admit late answers or settle a currently valid session too early. Three added H2 cases failed before the correction. Extend the timed write guard to read the active exam's current global end, derive the effective deadline from the locked persisted session and current schedule (ignoring the cached candidate deadline for existing sessions), and normalize the detached candidate timestamps before CAS. Keep archive delivery/mutation rules unchanged.

Independent reviewer dispatch was attempted twice but both failed at model capacity. Do not claim an independent verdict for this expiry-policy patch; retain the earlier successful reviews for earlier scopes and document this limitation.

## Final verified result

- Backend full suite: 747 tests, zero failures/errors/skips.
- Frontend full suite: 150 tests, 12 files.
- Webhook 7, dependency guards 4, credential hygiene 8: passed.
- Typecheck, isolated frontend production build, diff whitespace checks: passed. Official npm audit: zero findings, exit 0.
- Stale-schedule regressions: three demonstrated failures before fresh database schedule authority; targeted suite and full suite passed afterwards.
- Independent review limitation: two model-capacity failures, no verdict for this expiry phase; performed local manual review and documented the limitation.
- No pending policy questions or implementation steps remain in this confirmed-policy scope. No commit, push, deployment, production data access or tracked static regeneration.
