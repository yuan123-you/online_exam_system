package com.onlineexam.config;

import com.onlineexam.common.StoreHelper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * - 捕获未处理的运行时异常，返回统一格式的错误响应
 * - 避免将堆栈信息暴露给客户端
 * - 记录错误日志便于排查
 * - 对 SPA 路由的 404 返回 index.html
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        // SPA 路由: 非 API/非静态资源路径的 404，返回 index.html
        if (!path.startsWith("/api/") && !path.startsWith("/assets/")) {
            ClassPathResource indexHtml = new ClassPathResource("static/index.html");
            if (indexHtml.exists()) {
                byte[] content = indexHtml.getInputStream().readAllBytes();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.TEXT_HTML);
                return new ResponseEntity<>(content, headers, HttpStatus.OK);
            }
        }
        return StoreHelper.error(HttpStatus.NOT_FOUND, "资源未找到");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return StoreHelper.error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        // AI API 密钥未配置时返回 503 而非 500
        if (e.getMessage() != null && e.getMessage().contains("AI API 密钥未配置")) {
            log.warn("AI API 密钥未配置: {}", e.getMessage());
            return StoreHelper.error(HttpStatus.SERVICE_UNAVAILABLE, "AI 功能暂未启用，请联系管理员配置 AI API 密钥");
        }
        log.error("运行时异常", e);
        return StoreHelper.error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("未预期异常", e);
        return StoreHelper.error(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }
}
