package com.onlineexam;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamSnapshotSchemaTest {
  @Test
  void explanationMigrationAddsOnlyNullableQuestionMetadata() throws Exception {
    String schema = Files.readString(Path.of("src/main/resources/schema.sql"));
    String migration = Files.readString(Path.of("../db/migrations/2026-10-08-ai-question-explanation.sql"));
    assertTrue(schema.contains("explanation TEXT NULL"));
    assertTrue(migration.contains("ALTER TABLE question ADD COLUMN explanation TEXT NULL;"));
    assertFalse(migration.toUpperCase().contains("UPDATE EXAM"));
    assertFalse(migration.toUpperCase().contains("UPDATE QUESTION"));
  }

  @Test
  void additiveMigrationMatchesStartupSchemaAndDoesNotBackfillHistory() throws Exception {
    String schema = Files.readString(Path.of("src/main/resources/schema.sql"));
    String migration = Files.readString(Path.of("../db/migrations/2026-10-08-exam-snapshot.sql"));
    String marker = "CREATE TABLE IF NOT EXISTS exam_snapshot (";
    int start = schema.indexOf(marker);
    assertTrue(start >= 0);
    String ddl = schema.substring(start, schema.indexOf(';', start) + 1);
    assertEquals(ddl, migration.substring(migration.indexOf(marker)).strip());
    assertTrue(ddl.contains("exam_id VARCHAR(64) PRIMARY KEY"));
    assertTrue(ddl.contains("content_json JSON NOT NULL"));
    assertTrue(ddl.contains("FOREIGN KEY (exam_id) REFERENCES exam(id)"));
    assertFalse(migration.toUpperCase().contains("INSERT INTO"));
    assertFalse(migration.toUpperCase().contains("UPDATE EXAM"));
  }
}
