package com.onlineexam;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
class SubmissionRevisionSchemaTest {
  @Test void schemaAndManualMigrationDeclareServerRevisionAndKeepExistingUniqueKey() throws Exception {
    String schema=Files.readString(Path.of("src/main/resources/schema.sql"));
    assertTrue(schema.contains("revision BIGINT NOT NULL DEFAULT 0"));
    assertTrue(schema.contains("UNIQUE KEY uk_submission_exam_student (exam_id, student_id)"));
    String migration=Files.readString(Path.of("../db/migrations/2026-10-08-submission-revision.sql"));
    assertTrue(migration.contains("ALTER TABLE submission ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;"));
    assertFalse(migration.toUpperCase().contains("DELETE FROM")); assertFalse(migration.toUpperCase().contains("UPDATE SUBMISSION"));
  }
  @Test void additiveMigrationPreservesExistingResultsAndUniqueness() throws Exception {
    var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:revision_schema_"+java.util.UUID.randomUUID()+";MODE=MySQL", "sa", ""));
    // Keep the isolated database alive for the migration and verification only.
    try(var connection=jdbc.getDataSource().getConnection()) {
      jdbc.execute("create table submission(id varchar(64) primary key,exam_id varchar(64),student_id varchar(64),status varchar(20),final_score int, unique(exam_id,student_id))");
      jdbc.update("insert into submission values('s1','e1','u1','已完成',8)");
      String script=Files.readString(Path.of("../db/migrations/2026-10-08-submission-revision.sql"));
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      assertEquals(0L,jdbc.queryForObject("select revision from submission where id='s1'",Long.class));
      assertEquals(8,jdbc.queryForObject("select final_score from submission where id='s1'",Integer.class));
      assertEquals("已完成",jdbc.queryForObject("select status from submission where id='s1'",String.class));
      assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbc.update("insert into submission(id,exam_id,student_id,status,final_score) values('s2','e1','u1','进行中',0)"));
      jdbc.execute("drop all objects");
    }
  }
}
