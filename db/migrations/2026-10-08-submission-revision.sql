-- One-time schema-first migration: pause old submission writers, back up, and inspect the column before running.
-- The existing (exam_id,student_id) unique index must be present; inspect historical duplicate rows, never delete them automatically.
-- Old binaries do not advance revisions and must not write during mixed-version rollout.
ALTER TABLE submission ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
