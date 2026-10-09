-- Additive schema only. Do not reconstruct historical versions from the current question bank.
CREATE TABLE IF NOT EXISTS exam_snapshot (
  exam_id VARCHAR(64) PRIMARY KEY,
  content_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_exam_snapshot_exam FOREIGN KEY (exam_id) REFERENCES exam(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
