# Submission concurrency integrity implementation plan

**Goal:** Stale answer saves, duplicate starts/submits, manual regrades and deadline extensions cannot overwrite a newer persisted submission. Persisted extensions survive session reload.
**Architecture:** Add server-read revision metadata and guarded SQL updates, centralize existing duplicated submission write SQL behind a focused shared writer. New records use strict insert and existing unique exam/student constraint (already in schema), not destructive upsert. Every existing-record write checks revision plus identity/status; only one contender can succeed. Invalidate Store cache at transaction completion. No client-supplied version grants and no JVM mutex.
**Tech Stack:** Java21, JDBC/Spring transactions, JUnit/H2 isolated MySQL mode.
**Spec:** User continues comprehensive backend/database/AI remediation, frontend unchanged. Current source shows both StoreService and SubmissionRepository on-duplicate-key whole-row writes, stale candidate race; ExamService tests transient manualExtended boolean whereas only manualExtendedMinutes is stored.

## Constraints
- Preserve current partial/regrade scoring and existing unique constraint. No production DB operation or silent historic duplicate deletion.
- Additive schema-first manual migration only; verify deployed unique index and duplicates before use.
- Conflicting concurrent writes return conflict and require refresh rather than fabricate success. Already committed repeated submit retains existing success path.
- Do not bypass regression tests or loosen them. Preserve all unrelated dirty changes.
- Controllers use detached existing maps; shared snapshots must not be changed by rejected writes.

## Tasks
- [ ] Fresh baseline and exact failure identity capture.
- [ ] Failing real SQL tests for stale-save after submit, duplicate-key new ID not overwriting old row, concurrent CAS one winner, identity/state guards, rollback, Store writer/Repository same policy, revision read.
- [ ] Failing regression for persisted extension without transient manualExtended, rejected extension not modifying shared row.
- [ ] Shared guarded SQL writer, revision read/schema/manual migration, completion cache invalidation; detached extension + persistent deadline preservation.
- [ ] Focused and full regression compare; review; record deployment, old-instance stop and preflight unique-index requirement.


## Executed evidence
- Fresh pre-change fullsuite410tests10fail6error, exact identities saved.
-20initialSQL/deadline regressions allfailed; additive schema/badrevision3cases2fail1missingfileerror.
- Shared guard23passed; reviewfound legacy完成 spelling and late cache publication; both reproduced with2/2 failures thenfixed.
- Acceptance afterfix222tests0fail0error0skip,2026-10-08 21:27:40+08:00.
- Current init schema already uniqueexam/student; additive migration changes onlyrevision and preservesresults. Schema migration isolatedH2tested, no liveDB.
- One HTTP deliveryfixture now explicitlymodels affectedrow1; frozenquestion/privateanswer assertions retained. Cache callbacks/generation reviewreported no remaining important in-scopefindings.
- Final fullsuite/overallresults recordedinround6report. Remainingcrossworkflows/timewindow/wrongbook/targetMySQL are not markedcomplete.


Final parent full verification after all changes is running in exec3777; log backend/target/round6-exam-final-verified.log. Do not mark the fullsuite passing without observing its terminal result. Progress/remaining broader work are recorded in round6-three-platforms report.
