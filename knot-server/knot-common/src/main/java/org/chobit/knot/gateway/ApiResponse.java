package org.chobit.knot.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 返回体。成功时 {@code success=true}，失败时 {@code success=false}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, Integer code, String tags) {

    public ApiResponse {
        tags = (tags == null || tags.isBlank()) ? null : tags;
    }

    /**
     * Creates a success response.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "ok", data, null, null);
    }

    /**
     * Creates a success response with code and tags.
     */
    public static <T> ApiResponse<T> ok(T data, int successCode, String tags) {
        return new ApiResponse<>(true, "ok", data, successCode, tags);
    }

    /**
     * Creates a failure response.
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null, null, null);
    }

    /**
     * Creates a failure response with error code.
     */
    public static <T> ApiResponse<T> fail(int failCode, String message) {
        return new ApiResponse<>(false, message, null, failCode, null);
    }
}
