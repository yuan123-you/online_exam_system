CREATE TABLE IF NOT EXISTS department (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS class_info (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  major VARCHAR(100) NOT NULL,
  department_id VARCHAR(64) NOT NULL,
  INDEX idx_class_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_account (
  id VARCHAR(64) PRIMARY KEY,
  role VARCHAR(20) NOT NULL,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  name VARCHAR(50) NOT NULL,
  department_id VARCHAR(64),
  class_id VARCHAR(64),
  major VARCHAR(100),
  INDEX idx_user_role (role),
  INDEX idx_user_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS question (
  id VARCHAR(64) PRIMARY KEY,
  teacher_id VARCHAR(64) NOT NULL,
  subject VARCHAR(100) NOT NULL,
  knowledge_point VARCHAR(100) NOT NULL,
  difficulty VARCHAR(20) NOT NULL,
  type VARCHAR(20) NOT NULL,
  title TEXT NOT NULL,
  options_json JSON,
  answer_json JSON NOT NULL,
  score INT NOT NULL,
  source_tag VARCHAR(100),
  INDEX idx_question_teacher (teacher_id),
  INDEX idx_question_subject (subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS paper (
  id VARCHAR(64) PRIMARY KEY,
  teacher_id VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  duration_minutes INT NOT NULL,
  total_score INT NOT NULL,
  pass_score INT NOT NULL,
  question_ids_json JSON NOT NULL,
  paper_type VARCHAR(50),
  source_tag VARCHAR(100),
  INDEX idx_paper_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam (
  id VARCHAR(64) PRIMARY KEY,
  teacher_id VARCHAR(64) NOT NULL,
  paper_id VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  target_class_ids_json JSON NOT NULL,
  start_time DATETIME(3) NOT NULL,
  end_time DATETIME(3) NOT NULL,
  anti_cheat_limit INT NOT NULL,
  published TINYINT(1) NOT NULL DEFAULT 1,
  INDEX idx_exam_teacher (teacher_id),
  INDEX idx_exam_paper (paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS submission (
  id VARCHAR(64) PRIMARY KEY,
  exam_id VARCHAR(64) NOT NULL,
  student_id VARCHAR(64) NOT NULL,
  student_name VARCHAR(50) NOT NULL,
  answers_json JSON NOT NULL,
  answer_detail_json JSON,
  switch_count INT NOT NULL DEFAULT 0,
  suspicious TINYINT(1) NOT NULL DEFAULT 0,
  suspicious_reasons_json JSON,
  auto_score INT DEFAULT 0,
  final_score INT DEFAULT 0,
  status VARCHAR(20) NOT NULL,
  started_at DATETIME(3),
  deadline_at DATETIME(3),
  submitted_at DATETIME(3),
  updated_at DATETIME(3),
  manual_extended_minutes INT DEFAULT 0,
  graded_by VARCHAR(50),
  UNIQUE KEY uk_submission_exam_student (exam_id, student_id),
  INDEX idx_submission_exam (exam_id),
  INDEX idx_submission_student (student_id),
  INDEX idx_submission_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wrong_book_entry (
  id VARCHAR(64) PRIMARY KEY,
  student_id VARCHAR(64) NOT NULL,
  student_name VARCHAR(50),
  question_id VARCHAR(64) NOT NULL,
  subject VARCHAR(100),
  knowledge_point VARCHAR(100),
  type VARCHAR(20),
  title TEXT,
  latest_answer_json JSON,
  expected_answer_json JSON,
  last_retry_answer_json JSON,
  retry_count INT NOT NULL DEFAULT 0,
  wrong_count INT NOT NULL DEFAULT 0,
  full_score INT DEFAULT 0,
  last_score INT DEFAULT 0,
  last_wrong_at DATETIME(3),
  last_retry_at DATETIME(3),
  last_source_submission_id VARCHAR(64),
  last_source_exam_id VARCHAR(64),
  last_retry_correct TINYINT(1) NOT NULL DEFAULT 0,
  removable TINYINT(1) NOT NULL DEFAULT 0,
  removed_at DATETIME(3),
  status VARCHAR(20) DEFAULT 'active',
  UNIQUE KEY uk_wrong_student_question (student_id, question_id),
  INDEX idx_wrong_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_log (
  id VARCHAR(64) PRIMARY KEY,
  actor_id VARCHAR(64) NOT NULL,
  action VARCHAR(100) NOT NULL,
  detail TEXT,
  time DATETIME(3) NOT NULL,
  INDEX idx_log_time (time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
