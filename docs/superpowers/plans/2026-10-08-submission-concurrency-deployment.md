# Submission revision rollout and data preflight

Date: 2026-10-08. This phase changes backend persistence, not frontend. No actual database operation or deployment was executed.

## Current invariant

The initialized schema already contains UNIQUE KEY uk_submission_exam_student (exam_id, student_id). Strict creation relies on that database constraint. A conflict on either id or exam/student no longer acts as permission to overwrite a session. Existing-row writes require server-read revision metadata, immutable identity and an allowed source status. One winner increments revision atomically; stale contenders receive HTTP409 and must refresh. The frontend never grants a revision through its body. Ordinary resubmission of a persisted final record keeps the existing response path.

## Before rollout

Pause all old submission writers (start/detail saves, autosave, submit, manual grade, extension) across all instances. Back up and verify restore procedures. Old binaries do not increment revisions, so mixed old/new writes break the guard invariant.

Read-only operator preflight:

```sql
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='submission' AND COLUMN_NAME='revision';

SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='submission'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT exam_id, student_id, COUNT(*) AS records
FROM submission
GROUP BY exam_id, student_id HAVING COUNT(*)>1;
```

Confirm the unique index covers exactly the intended exam/student pair, not merely that its name exists. If absent or historical duplicates exist, stop rollout; keep all original attempts/grades and determine canonical-history policy with the user. Do not delete arbitrary duplicate attempts to force an index to pass. The migration does not auto-add a duplicate-named index or rewrite existing grades.

## Additive migration

If revision is absent, execute once:

D:\Codex Web\online_system\db\migrations\2026-10-08-submission-revision.sql

The column is BIGINT NOT NULL DEFAULT0. Existing records begin at revision0 without changing their ID, status, answers, scores, random question/option order or deadlines. If the column already exists, do not repeat ADD COLUMN; verify its type/default/non-null behavior and compatibility. CREATE TABLE IF NOT EXISTS does not add the column to existing tables.

After schema and uniqueness checks, switch all writers to new code, then reopen traffic. Check stale-save/submit, duplicate first-start, two concurrent grade/extension operations and fresh conflict recovery against isolated target MySQL first. The local tests exercise H2 and do not certify the production driver's locking/isolation/row counts.

## Deadline extension

Persistent manualExtendedMinutes, not only transient manualExtended, preserves the saved deadline on session reload. Current extension semantics (including the existing exam-end limits) were not silently redefined. The UI is unchanged. Extension candidates are detached so a failed CAS does not modify shared cached rows.

## Cache

Submission writes invalidate the shared Store cache before writing and at outer transaction completion. A generation guard prevents an old load from publishing a snapshot after an intervening invalidation. The triggering reader may still finish with its original in-flight view; database CAS prevents it from overwriting newer facts. Other endpoints' mutable Store edits and broader fail-open reads remain separate remediation work, not claimed solved here.

## Rollback / failure

Keep the additive column and recorded revisions. Stop writes before considering old-code rollback; old-code upsert can reopen or overwrite submitted sessions and does not advance revisions. SQL409 is a business conflict, not authorization to retry with a guessed revision. Refresh the authoritative server record. No durable external-effect mechanism or reliable wrong-book retry was added by this phase.
