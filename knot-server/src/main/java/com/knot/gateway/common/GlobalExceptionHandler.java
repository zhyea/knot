package com.knot.gateway.common;

import com.knot.gateway.common.error.BusinessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<ErrorPayload> handleBusinessException(BusinessException ex) {
        return ApiResponse.fail(ex.getMessage() + " (" + ex.getCode() + ")");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    public record ErrorPayload(String code, String message) {
    }
}
