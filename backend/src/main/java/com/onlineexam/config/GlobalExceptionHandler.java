package com.onlineexam.config;

import com.onlineexam.common.StoreHelper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 全局异常处理器
 * - 捕获未处理的运行时异常，返回统一格式的错误响应
 * - 避免将堆栈信息暴露给客户端
 * - 记录错误日志便于排查
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return StoreHelper.error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return StoreHelper.error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("未预期异常", e);
        return StoreHelper.error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }
}
