package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = GatewayController.class)
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestHeader(MissingRequestHeaderException e) {
        return error(HttpStatus.BAD_REQUEST,
                "Missing required header: " + e.getHeaderName(),
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code());
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(Exception e) {
        log.warn("Invalid gateway request", e);
        return error(HttpStatus.BAD_REQUEST,
                "Invalid request body",
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code());
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        HttpStatus status = statusOf(e.getCode());
        String type = ErrorCode.UNAUTHORIZED.code().equals(e.getCode())
                ? GatewayErrorTypeEnum.AUTH_ERROR.code()
                : GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code();
        log.warn("Gateway business exception: code={}, message={}", e.getCode(), e.getMessage());
        return error(status, e.getMessage(), type, e.getCode());
    }

    /**
     * Handles the incoming request flow. Executes the public operation.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unexpected gateway error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                GatewayErrorTypeEnum.UPSTREAM_ERROR.code());
    }

    private static HttpStatus statusOf(String code) {
        if (ErrorCode.UNAUTHORIZED.code().equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ErrorCode.NOT_FOUND.code().equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (ErrorCode.CONFLICT.code().equals(code)) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String type) {
        return error(status, message, type, null);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status,
                                                            String message,
                                                            String type,
                                                            String code) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        error.put("type", type);
        if (code != null && !code.isBlank()) {
            error.put("code", code);
        }
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
