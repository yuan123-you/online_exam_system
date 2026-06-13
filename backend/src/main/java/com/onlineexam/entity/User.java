package com.onlineexam.entity;

/**
 * 用户实体（包含管理员、教师、学生）
 */
public record User(
    String id,
    String role,
    String username,
    String password,
    String name,
    String departmentId,
    String classId,
    String major
) {}
