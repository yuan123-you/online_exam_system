package com.onlineexam.entity;

import java.util.List;

/**
 * 错题本条目实体
 */
public record WrongBookEntry(
    String id,
    String studentId,
    String studentName,
    String questionId,
    String subject,
    String knowledgePoint,
    String type,
    String title,
    List<Object> latestAnswer,
    List<Object> expectedAnswer,
    List<Object> lastRetryAnswer,
    int retryCount,
    int wrongCount,
    int fullScore,
    int lastScore,
    String lastWrongAt,
    String lastRetryAt,
    String lastSourceSubmissionId,
    String lastSourceExamId,
    boolean lastRetryCorrect,
    boolean removable,
    String removedAt,
    String status
) {}
