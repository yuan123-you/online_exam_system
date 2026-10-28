# Current Project Code Review and Repair Implementation Plan

> Execute locally, task by task, with a failing regression test before each production fix. No delegation, commits, pushes, or deployment are required.

**Goal:** Review the current online examination system and repair confirmed defects with minimal, regression-tested changes.

**Architecture:** Use the installed OpenCodeReview full-file preview and delegated language rules as review inputs. Trace frontend requests through controllers, services, and persistence, then test and repair only demonstrated defects.

**Tech Stack:** Vue 3, TypeScript, Vitest, Spring Boot 3, Java 21, Maven, JUnit, Mockito, H2.

**Spec:** User request on 2026-10-28: review and repair the current project; user-provided Karpathy AGENTS.md guidelines.

## Global Constraints

- Clarify ambiguous business behavior instead of inventing requirements.
- Make minimal targeted modifications; do not refactor unrelated code.
- Reproduce bugs with tests before production changes.
- Never print or transmit local secrets, contact production services, or modify production data.
- Do not claim verification beyond checks actually executed.

## Task 1: Inventory and baseline

**Files:** Inspect `package.json`, `backend/pom.xml`, `src/`, `backend/src/main/java/`, `backend/src/test/`, `scripts/`, and existing project documentation.

- [x] Confirm repository scope, clean worktree, and available test tools.
- [x] Read OpenCodeReview CLI scan/delegation usage and resolve Java/TypeScript rules.
- [x] Save the explicit full-file scan inventory outside tracked source.
- [x] Run existing frontend tests, type check, and backend tests using the installed JDK 21.

## Task 2: Trace high-risk workflows and repair confirmed defects

**Files:** Exact production and regression test paths will be recorded below after root-cause investigation, before each fix.

- [x] Review authentication/data isolation, exam snapshots and lifecycle, submission/grading, AI streaming/practice, frontend state/rendering, and deployment/build scripts.
- [x] For each confirmed issue: document actual and expected behavior, write a failing regression test, and run it to demonstrate failure.
- [x] Apply only the smallest necessary production correction.
- [x] Re-run the regression tests and relevant neighboring tests.

## Task 3: Final verification and audit report

**Files:** Create `docs/code-review-2026-10-28.md`; update this plan with discovered issue paths and evidence.

- [x] Run frontend full suite, TypeScript check, backend full suite, secret-hygiene tests, and a frontend build that does not overwrite tracked bundled assets.
- [x] Inspect `git diff --check` and the complete final patch for accidental changes.
- [x] Record findings, repairs, exact commands/results, and remaining review limitations.
## Confirmed repair scopes (recorded after failing regressions)

1. `backend/src/main/java/com/onlineexam/service/PracticeSessionService.java` and `backend/src/test/java/com/onlineexam/service/PracticeSessionIntegrityTest.java`: normalize supported string answers before casting; propagate database write failures so replacement, answer saving, submission and deletion roll back.
2. `backend/src/main/java/com/onlineexam/service/SubmissionService.java` and `backend/src/test/java/com/onlineexam/service/ChoiceSubmissionIntegrityTest.java`: require exactly one submitted/reference answer for single-choice and judgment scoring.
3. `src/views/teacher/ClassAnalysisView.vue` and adjacent test: invalidate stale requests and clear obsolete data when exam selection changes.
4. `src/composables/useResponsiveLayout.ts`, `src/components/teacher/PaperPreviewModal.vue`, `src/components/common/PaginationBar.test.ts`, `src/views/teacher/ClassAnalysisView.vue`, `package.json`: repair demonstrated type-check failures without broad compiler relaxation; add a repeatable typecheck command.

Red evidence: 4 frontend race tests failed; backend regression run had 8 assertion failures and 2 string-cast errors (12 tests total). Baseline vue-tsc exited 2.
5. `auto-deploy-webhook.js`, `tests/auto-deploy-webhook.test.cjs`, `package.json`: fail closed without WEBHOOK_SECRET and validate header format before constant-time comparison. Red evidence: 4 of 7 isolated Node tests fail; no real server, filesystem logging, or deployment process is invoked by these tests.
6. `package.json` / `package-lock.json`: npm audit against the official registry reports 14 affected dependency nodes (10 high, 3 moderate, 1 low). First attempt only semver-compatible updates of affected Vue/Vite/Vitest and transitive nodes, with lifecycle scripts disabled. Cross-major changes to happy-dom, PDF.js and KaTeX require separate compatibility decisions; no force upgrade or suggested PDF.js downgrade. The initial configured mirror has no audit endpoint; official registry override is per-command only. `npm audit fix --dry-run` failed in npm's resolver (`edgesOut`), so use an explicit scoped update rather than force.

## Final evidence

- Backend full suite: 712 passed, zero failures/errors/skips.
- Frontend after dependency updates: 140 passed; typecheck and production build passed.
- Webhook: 7 passed; credential hygiene: 8 passed.
- Independent patch review found no introduced production defects; both requested rollback coverage gaps now have direct tests.
- Scoped dependency repairs reduce audit-affected nodes from 14 to 3; remaining PDF.js/happy-dom high and KaTeX low are explicitly unresolved, not reported as passing.
- Existing global exam-end/automatic-submission policy needs user confirmation before a safe behavior change.
- No deployment, push, commit, production data access, or tracked static-bundle regeneration.
- See docs/code-review-2026-10-28.md for the complete result and limitations.

## Follow-up superseding first-pass dependency status

The user requested full repairs. Remaining npm findings were subsequently reduced to zero; effective teacher extensions and finished-session detection were repaired with nine additional backend tests. Latest counts: backend 721, frontend 142, dependency guards 4, webhook 7, credential hygiene 8. See docs/superpowers/plans/2026-10-28-remaining-review-fixes.md. Expiry/grace policy still awaits explicit clarification, and is not marked complete.

## Confirmed expiry policy completed

User confirmation was received and the remaining expiry policy has been implemented, including background settlement, current-database deadline authority, row-lock rechecks and UI clock/extension synchronization. Final results: backend 747, frontend 150, Webhook 7, dependency guards 4, hygiene 8; typecheck/build/diff pass and npm audit zero. Earlier historical statements about awaiting policy confirmation are superseded. No deployment or commit was performed.
