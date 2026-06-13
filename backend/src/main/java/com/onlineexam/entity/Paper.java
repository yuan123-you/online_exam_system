package com.onlineexam.entity;

import java.util.List;

/**
 * 试卷实体
 */
public record Paper(
    String id,
    String teacherId,
    String name,
    int durationMinutes,
    int totalScore,
    int passScore,
    List<Object> questionIds,
    String paperType,
    String sourceTag
) {}
