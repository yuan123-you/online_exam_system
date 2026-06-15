CREATE TABLE IF NOT EXISTS department (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  created_by VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS class_info (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  major VARCHAR(100) NOT NULL,
  department_id VARCHAR(64) NOT NULL,
  created_by VARCHAR(64),
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
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_question_teacher (teacher_id),
  INDEX idx_question_subject (subject),
  INDEX idx_question_deleted (deleted)
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
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_paper_teacher (teacher_id),
  INDEX idx_paper_deleted (deleted)
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
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  INDEX idx_exam_teacher (teacher_id),
  INDEX idx_exam_paper (paper_id),
  INDEX idx_exam_deleted (deleted)
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
  question_order_json JSON,
  option_order_json JSON,
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

-- Migration: add question_order_json to submission table
-- ALTER TABLE submission ADD COLUMN question_order_json JSON;

-- ========== 个性化推荐相关表 ==========

-- 用户行为日志：记录用户在系统中的各种交互行为
CREATE TABLE IF NOT EXISTS user_behavior_log (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  action VARCHAR(50) NOT NULL COMMENT '行为类型: page_view/chat/practice/exam_submit/wrongbook_retry/explain_answer/recommendation_click',
  target_type VARCHAR(50) COMMENT '目标类型: question/subject/knowledge_point/page/conversation',
  target_id VARCHAR(64) COMMENT '目标ID',
  detail JSON COMMENT '行为详情(如题目信息、页面路径等)',
  duration_ms INT DEFAULT 0 COMMENT '行为持续时间(毫秒)',
  created_at DATETIME(3) NOT NULL,
  INDEX idx_behavior_user (user_id),
  INDEX idx_behavior_action (action),
  INDEX idx_behavior_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户画像：存储AI分析后的用户学习画像数据
CREATE TABLE IF NOT EXISTS user_profile (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL UNIQUE,
  learning_style VARCHAR(20) DEFAULT 'balanced' COMMENT '学习风格: visual/auditory/reading/kinesthetic/balanced',
  difficulty_preference VARCHAR(20) DEFAULT 'medium' COMMENT '难度偏好: easy/medium/hard/adaptive',
  activity_level VARCHAR(20) DEFAULT 'moderate' COMMENT '活跃度: low/moderate/high',
  subject_affinity JSON COMMENT '科目亲和度 [{subject, score, trend}]',
  knowledge_gaps JSON COMMENT '知识薄弱点 [{knowledgePoint, subject, severity}]',
  learning_goals JSON COMMENT '学习目标 [{goal, progress, deadline}]',
  study_patterns JSON COMMENT '学习模式 {peakHours, avgSessionMin, preferredTypes, weeklyFrequency}',
  ai_summary TEXT COMMENT 'AI生成的用户画像摘要',
  recommendations_json JSON COMMENT '当前推荐列表缓存',
  recommendations_updated_at DATETIME(3) COMMENT '推荐列表最后更新时间',
  profile_version INT DEFAULT 1 COMMENT '画像版本号',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  INDEX idx_profile_user (user_id),
  INDEX idx_profile_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 推荐反馈：用户对推荐内容的反馈
CREATE TABLE IF NOT EXISTS recommendation_feedback (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  recommendation_type VARCHAR(50) NOT NULL COMMENT '推荐类型: practice/chat/subject_review/knowledge_gap/study_plan',
  recommendation_content JSON NOT NULL COMMENT '推荐的具体内容',
  feedback_type VARCHAR(20) NOT NULL COMMENT '反馈类型: helpful/not_helpful/irrelevant/too_easy/too_hard/bookmark',
  feedback_detail TEXT COMMENT '用户文字反馈',
  created_at DATETIME(3) NOT NULL,
  INDEX idx_feedback_user (user_id),
  INDEX idx_feedback_type (recommendation_type),
  INDEX idx_feedback_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 练习会话持久化相关表 ==========

-- 练习会话：记录用户每次练习的整体状态
CREATE TABLE IF NOT EXISTS practice_session (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) COMMENT '关联的聊天会话ID',
  subject VARCHAR(100) COMMENT '练习科目',
  question_count INT NOT NULL DEFAULT 0 COMMENT '题目数量',
  correct_count INT NOT NULL DEFAULT 0 COMMENT '正确数量',
  total_score INT NOT NULL DEFAULT 0 COMMENT '总分数',
  earned_score INT NOT NULL DEFAULT 0 COMMENT '获得分数',
  status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active/submitted/abandoned',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  submitted_at DATETIME(3) COMMENT '提交时间',
  INDEX idx_ps_user (user_id),
  INDEX idx_ps_status (status),
  INDEX idx_ps_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 练习题目：记录每道练习题的详细内容和用户答题状态
CREATE TABLE IF NOT EXISTS practice_question (
  id VARCHAR(64) PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL COMMENT '所属练习会话ID',
  user_id VARCHAR(64) NOT NULL,
  question_index INT NOT NULL DEFAULT 0 COMMENT '题目在会话中的序号',
  question_data JSON NOT NULL COMMENT '完整题目数据(title,type,options,answer,score,explanation等)',
  user_answer_json JSON COMMENT '用户答案',
  is_correct TINYINT(1) COMMENT '是否正确',
  is_submitted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已提交',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  submitted_at DATETIME(3) COMMENT '提交时间',
  INDEX idx_pq_session (session_id),
  INDEX idx_pq_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 题库备份：教师题库快照
CREATE TABLE IF NOT EXISTS question_backup (
  id VARCHAR(64) PRIMARY KEY,
  teacher_id VARCHAR(64) NOT NULL,
  questions_json JSON NOT NULL COMMENT '备份的题目数据快照',
  question_count INT NOT NULL DEFAULT 0 COMMENT '备份题目数量',
  created_at DATETIME(3) NOT NULL,
  INDEX idx_backup_teacher (teacher_id),
  INDEX idx_backup_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 站内通知系统 ==========

CREATE TABLE IF NOT EXISTS notification (
  id VARCHAR(64) PRIMARY KEY,
  sender_id VARCHAR(64) NOT NULL,
  target_role VARCHAR(20) COMMENT '目标角色: student/teacher/all，NULL表示指定用户',
  target_class_id VARCHAR(64) COMMENT '目标班级ID，NULL表示所有班级',
  target_user_id VARCHAR(64) COMMENT '目标用户ID（单条通知），NULL表示批量',
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  type VARCHAR(30) NOT NULL DEFAULT 'general' COMMENT '类型: general/exam/grade/system',
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  read_at DATETIME(3),
  created_at DATETIME(3) NOT NULL,
  INDEX idx_notif_target_user (target_user_id),
  INDEX idx_notif_target_role (target_role),
  INDEX idx_notif_target_class (target_class_id),
  INDEX idx_notif_created (created_at),
  INDEX idx_notif_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
