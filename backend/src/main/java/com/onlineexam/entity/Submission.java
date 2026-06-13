package com.onlineexam.entity;

import java.util.List;
import java.util.Map;

/**
 * 提交记录实体
 */
public record Submission(
    String id,
    String examId,
    String studentId,
    String studentName,
    List<Object> answers,
    List<Object> answerDetail,
    int switchCount,
    boolean suspicious,
    List<Object> suspiciousReasons,
    int autoScore,
    int finalScore,
    String status,
    String startedAt,
    String deadlineAt,
    String submittedAt,
    String updatedAt,
    int manualExtendedMinutes,
    String gradedBy,
    List<Object> questionOrder,
    Map<String, Object> optionOrder
) {}
