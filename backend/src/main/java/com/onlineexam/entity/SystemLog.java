package com.onlineexam.entity;

/**
 * 系统日志实体
 */
public record SystemLog(
    String id,
    String actorId,
    String action,
    String detail,
    String time
) {}
