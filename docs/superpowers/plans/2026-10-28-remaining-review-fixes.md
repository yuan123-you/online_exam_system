# Remaining Code Review Repairs Implementation Plan

**Goal:** Remove the three remaining npm advisory findings and repair confirmed exam-lifecycle bugs without inventing a late-submission business policy.

**Architecture:** Prefer the smallest safe dependency change. PDF.js has no code references, so remove its unused dependency rather than adding or migrating PDF functionality. Upgrade happy-dom and KaTeX to verified patched releases; add regression guards and a KaTeX inherited-trust reproducer. Trace exam lifecycle against existing tests before any behavioral change.

**Tech Stack:** Vue 3 / Vitest / happy-dom, KaTeX, Node 24 / npm 11, Spring Boot / JDK 21 / JUnit.

**Spec:** User follow-up on 2026-10-28: fully repair the current project; existing user AGENTS guidelines require clarifying undefined requirements and reproducing defects before fixes.

## Constraints

- Preserve all previous uncommitted repairs.
- Do not access production data, real AI providers, or secrets.
- Do not deploy, push, commit, or regenerate tracked static bundles.
- Do not change documented default-password behavior without an explicit policy change.
- User explicitly confirmed saved-server-answers-only expiry settlement, no late edits or grace period, and valid teacher extensions; implementation and verification completed in the confirmed-expiry plan.

## Task 1: Remaining dependencies

**Files:** `package.json`, `package-lock.json`, `tests/dependency-security.test.cjs`, `src/utils/markdown.test.ts`, `README.md`.

- [x] Verify current audit: three affected nodes (two high, one low).
- [x] Verify patched releases in primary advisory/official npm metadata and confirm no PDF.js references in tracked application code.
- [x] Write and run failing dependency guards and inherited-trust regression.
- [x] Remove unused PDF.js; upgrade happy-dom and KaTeX to patched versions without lifecycle scripts.
- [x] Run dependency guards, all frontend tests, typecheck and isolated production build.
- [x] Verify official npm audit reports zero affected nodes.

## Task 2: Exam-lifecycle correctness

**Files:** inspect `ExamService.java`, `SubmissionController.java`, `ExamSessionModal.vue`, existing exam/session/concurrency tests. Record exact repair scope before any production patch.

- [x] Identify already-defined lifecycle contracts versus undefined expiry/grace behavior.
- [x] Reproduce confirmed defects and make only justified minimal fixes.
- [x] Implement expiry policy after explicit user confirmation; verified by the confirmed-expiry implementation plan.

## Task 3: Final verification and report

**Files:** `docs/code-review-2026-10-28.md`, this plan.

- [x] Run all applicable tests/checks and inspect the diff.
- [x] Update the report with precise commands, results and any remaining policy decision.
## Confirmed lifecycle repair scope (independent of unanswered expiry policy)

`backend/src/main/java/com/onlineexam/service/ExamService.java` and new `backend/src/test/java/com/onlineexam/service/ExamSessionLifecycleTest.java`:

- Existing teacher-extension code deliberately persists an individual deadline beyond the global exam end, but ensureStudentSession rejects it before consulting that record. Honor only an existing RUNNING session with a valid unexpired manual extension; do not permit new sessions or extend an expired session automatically.
- Completed/pending submissions must return the existing finished-session sentinel even after the global end, so the already-implemented controller finished-state response remains reachable.
- Tests cover both services and real controller flow, completed/pending results, rejected expired/missing extension, and no new session creation. This does not decide whether late answer payloads may be accepted or invent any grace interval.

## Verification evidence so far

- Before: dependency guards had three failures; inherited-trust renderer regression failed; lifecycle suite had six failures out of nine.
- After: official npm audit zero findings; dependency guards four pass; frontend 142 pass; isolated production build and full npm dependency tree pass.
- Teacher-extension/finished-session subset passes. Full backend suite: 721 pass, zero failures/errors/skips; independent scoped follow-up review approved with no findings.
- Expiry/grace behavior remains awaiting explicit user clarification, not completed.

Final follow-up checks: frontend 142, backend 721, dependency guards 4, webhook 7 and credential hygiene 8 passed; typecheck/build/peer-tree/diff checks passed; npm audit 0. The requested late-submission policy clarification remains the only open step in this plan.

## User confirmation received

On 2026-10-28 the user explicitly confirmed saved-server-answers-only expiry settlement, no late edits, and valid teacher extensions. The previously open policy task is being implemented and verified in docs/superpowers/plans/2026-10-28-confirmed-expiry-policy.md. Historical statements above about awaiting confirmation are superseded by this authorization.

## Final completion superseding historical pending status

The user-confirmed expiry policy has now been implemented and verified. Latest full results: backend 747; frontend 150; Webhook 7; dependency guards 4; credential hygiene 8. Typecheck/build/diff pass; official npm audit zero. No open policy or implementation checkbox remains. See the confirmed-expiry plan for row-lock/current-schedule authority and review-capacity limitations.
