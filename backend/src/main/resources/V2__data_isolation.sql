-- V2__data_isolation.sql
-- Phase 4: Data isolation columns
ALTER TABLE department ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);

-- Phase 7: Soft delete support
ALTER TABLE question ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE paper ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0;

-- Indexes for soft delete filtering
CREATE INDEX IF NOT EXISTS idx_question_deleted ON question(deleted);
CREATE INDEX IF NOT EXISTS idx_paper_deleted ON paper(deleted);
CREATE INDEX IF NOT EXISTS idx_exam_deleted ON exam(deleted);
