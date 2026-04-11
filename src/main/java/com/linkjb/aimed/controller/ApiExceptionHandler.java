package com.linkjb.aimed.controller;

import com.linkjb.aimed.bean.ApiErrorResponse;
import com.linkjb.aimed.config.skywalk.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.time.OffsetDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiErrorResponse> handleIo(IOException exception, HttpServletRequest request) {
        log.warn("api.exception.io path={} message={}", request.getRequestURI(), exception.getMessage(), exception);
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException exception, HttpServletRequest request) {
        log.warn("api.exception.bad-request path={} message={}", request.getRequestURI(), exception.getMessage(), exception);
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException exception, HttpServletRequest request) {
        log.warn("api.exception.conflict path={} message={}", request.getRequestURI(), exception.getMessage(), exception);
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleRedis(RedisConnectionFailureException exception, HttpServletRequest request) {
        log.error("api.exception.redis-connection path={} message={}", request.getRequestURI(), exception.getMessage(), exception);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Redis 未连接，当前无法处理验证码或登录态", request);
    }

    @ExceptionHandler(RedisSystemException.class)
    public ResponseEntity<ApiErrorResponse> handleRedisSystem(RedisSystemException exception, HttpServletRequest request) {
        log.error("api.exception.redis-auth path={} message={}", request.getRequestURI(), exception.getMessage(), exception);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Redis 认证失败，请检查 Redis 用户名或密码配置", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getDefaultMessage();
        log.warn("api.exception.validation path={} message={}", request.getRequestURI(), message, exception);
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus resolved = status == null ? HttpStatus.BAD_REQUEST : status;
        String message = exception.getReason() == null ? "请求处理失败" : exception.getReason();
        log.warn("api.exception.status path={} status={} message={}", request.getRequestURI(), resolved.value(), message, exception);
        return buildResponse(resolved, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("api.exception.unexpected path={} message={}", request.getRequestURI(), exception.getMessage(), exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部异常，请稍后重试。", request);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setTraceId(MDC.get(RequestTraceFilter.TRACE_ID_KEY));
        response.setPath(request.getRequestURI());
        response.setMessage(message);
        return ResponseEntity.status(status).body(response);
    }
}
