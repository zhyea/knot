package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayRequestException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.GatewayErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice(assignableTypes = GatewayController.class)
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /**
     * Handles missing required gateway headers.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public GatewayErrorResponse handleMissingRequestHeader(MissingRequestHeaderException e) {
        return GatewayErrorResponse.of(
                "Missing required header: " + e.getHeaderName(),
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code()
        );
    }

    /**
     * Handles malformed gateway requests.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public GatewayErrorResponse handleInvalidRequest(Exception e) {
        log.warn("Invalid gateway request", e);
        return GatewayErrorResponse.of(
                "Invalid request body",
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code()
        );
    }

    /**
     * Handles gateway authentication errors.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(GatewayAuthException.class)
    public GatewayErrorResponse handleGatewayAuthException(GatewayAuthException e) {
        log.warn("Gateway authentication exception: message={}", e.getMessage());
        return gatewayError(e);
    }

    /**
     * Handles gateway request validation errors.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(GatewayInvalidRequestException.class)
    public GatewayErrorResponse handleGatewayInvalidRequestException(GatewayInvalidRequestException e) {
        log.warn("Gateway request exception: message={}", e.getMessage());
        return gatewayError(e);
    }

    /**
     * Handles gateway rate limit errors.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(GatewayRateLimitException.class)
    public GatewayErrorResponse handleGatewayRateLimitException(GatewayRateLimitException e) {
        log.warn("Gateway rate limit exception: code={}, message={}", e.code(), e.getMessage());
        return gatewayError(e);
    }

    /**
     * Handles upstream gateway errors.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(GatewayUpstreamException.class)
    public GatewayErrorResponse handleGatewayUpstreamException(GatewayUpstreamException e) {
        log.warn("Gateway upstream exception: code={}, message={}", e.code(), e.getMessage());
        return gatewayError(e);
    }

    /**
     * Handles unexpected business errors from gateway dependencies.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BusinessException.class)
    public GatewayErrorResponse handleBusinessException(BusinessException e) {
        log.warn("Gateway business exception: code={}, message={}", e.getCode(), e.getMessage());
        return GatewayErrorResponse.of(
                e.getMessage(),
                GatewayErrorTypeEnum.INVALID_REQUEST_ERROR.code(),
                e.getCode()
        );
    }

    /**
     * Handles unexpected gateway failures.
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public GatewayErrorResponse handleException(Exception e) {
        log.error("Unexpected gateway error", e);
        return GatewayErrorResponse.of(
                "Internal server error",
                GatewayErrorTypeEnum.UPSTREAM_ERROR.code()
        );
    }

    private static GatewayErrorResponse gatewayError(GatewayRequestException e) {
        return GatewayErrorResponse.of(e.getMessage(), e.type(), e.code());
    }
}
