package com.onlineexam.entity;

import java.util.List;

/**
 * 考试实体
 */
public record Exam(
    String id,
    String teacherId,
    String paperId,
    String name,
    List<Object> targetClassIds,
    String startTime,
    String endTime,
    int antiCheatLimit,
    boolean published
) {}
