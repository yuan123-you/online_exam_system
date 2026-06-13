package com.onlineexam.entity;

import java.util.List;

/**
 * 题目实体
 */
public record Question(
    String id,
    String teacherId,
    String subject,
    String knowledgePoint,
    String difficulty,
    String type,
    String title,
    List<Object> options,
    List<Object> answer,
    int score,
    String sourceTag
) {}
