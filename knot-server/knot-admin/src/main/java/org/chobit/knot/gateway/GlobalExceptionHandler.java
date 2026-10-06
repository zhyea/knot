package org.chobit.knot.gateway;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ForbiddenException;
import org.chobit.knot.gateway.error.UnauthorizedException;
import org.chobit.knot.gateway.rw.RwProperties;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器，统一返回 {@link ApiResponse}，避免再被
 * {@link org.chobit.knot.gateway.rw.ApiResponseWrapperAdvice} 二次包装。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final RwProperties rwProperties;

    /**
     * Constructs a new instance.
     */
    public GlobalExceptionHandler(RwProperties rwProperties) {
        this.rwProperties = rwProperties;
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException e) {
        log.warn("Unauthorized: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(rwProperties.getFailCode(), e.getMessage());
    }

    /**
     * 已登录但无权限：返回 403，不能返回 401 —— 前端会把 401 当作「登录已过期」并强制登出。
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenException e) {
        log.warn("Forbidden: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(rwProperties.getFailCode(), e.getMessage());
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.fail(rwProperties.getFailCode(), e.getMessage());
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("Validation failed: {}", message);
        return ApiResponse.fail(rwProperties.getFailCode(), message);
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        log.warn("Bind failed: {}", message);
        return ApiResponse.fail(rwProperties.getFailCode(), message);
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return ApiResponse.fail(rwProperties.getFailCode(), e.getMessage());
    }

    /**
     * 未匹配到任何 handler（含不存在的路径 / 被删除的端点）。
     *
     * <p>Spring 6.1+ 对未匹配路径抛 {@link NoResourceFoundException}，若不拦截会落到
     * {@code handleException} 变成 500；此处显式返回 404，便于前端区分「接口不存在」与「服务端故障」。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("No handler for {}: {}", e.getResourcePath(), e.getMessage());
        return ApiResponse.fail(rwProperties.getFailCode(), "接口不存在: " + e.getResourcePath());
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ApiResponse.fail(rwProperties.getFailCode(), "系统内部错误: " + e.getMessage());
    }
}
