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

-- Chat system tables (not present in full_dump.sql)
CREATE TABLE IF NOT EXISTS chat_conversation (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'student',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  INDEX idx_chat_conv_user (user_id),
  INDEX idx_chat_conv_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
  id VARCHAR(64) PRIMARY KEY,
  conversation_id VARCHAR(64) NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  reasoning TEXT,
  created_at DATETIME(3) NOT NULL,
  INDEX idx_chat_msg_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
