package com.assessment.packing.web.error;

import com.assessment.packing.web.dto.ApiErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public final class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorDto> api(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), Map.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorDto> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.putIfAbsent("request", error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "请求参数不合法，请修正后重试", fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorDto> unreadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "请求体无法解析，请检查 JSON 字段和枚举值", Map.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorDto> notFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "请求的资源不存在", Map.of(), request);
    }

    /**
     * SSE 客户端关闭页面或刷新时，容器会用这个异常通知写端连接已经消失。
     * 这是长连接的正常生命周期，响应通常也已经提交；既不能再写一份 JSON，
     * 也不应按服务端故障打印整段 ERROR 堆栈。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void disconnected(AsyncRequestNotUsableException exception) {
        LOGGER.debug("异步客户端已断开：{}", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorDto> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("未处理的 API 异常，requestId={}", requestId(request), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "服务器处理请求时发生错误，可使用请求标识查询日志", Map.of(), request);
    }

    private static ResponseEntity<ApiErrorDto> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fields,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                new ApiErrorDto(code, message, fields, requestId(request), Instant.now()));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value instanceof String text ? text : "unavailable";
    }
}
