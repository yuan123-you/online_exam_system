-- Migration: Add missing columns for compatibility with StoreService v2
-- Run this on the server: mysql -u root -p online_exam_system < db/migration_add_columns.sql

ALTER TABLE department ADD COLUMN IF NOT EXISTS created_by VARCHAR(64) DEFAULT NULL;
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS created_by VARCHAR(64) DEFAULT NULL;

ALTER TABLE question ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE paper ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE exam ADD COLUMN IF NOT EXISTS deleted TINYINT(1) NOT NULL DEFAULT 0;

-- Add indexes for deleted columns
ALTER TABLE question ADD INDEX IF NOT EXISTS idx_question_deleted (deleted);
ALTER TABLE paper ADD INDEX IF NOT EXISTS idx_paper_deleted (deleted);
ALTER TABLE exam ADD INDEX IF NOT EXISTS idx_exam_deleted (deleted);
