package org.chobit.knot.gateway;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.UnauthorizedException;
import org.chobit.knot.gateway.rw.RwProperties;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 鍏ㄥ眬寮傚父澶勭悊鍣紙杩斿洖 {@link ApiResponse}锛屼笉缁?{@link org.chobit.knot.gateway.rw.ApiResponseWrapperAdvice} 浜屾鍖呰锛夈€?
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
                .orElse("鍙傛暟鏍￠獙澶辫触");
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
                .orElse("鍙傛暟缁戝畾澶辫触");
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
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ApiResponse.fail(rwProperties.getFailCode(), "绯荤粺鍐呴儴閿欒: " + e.getMessage());
    }
}
