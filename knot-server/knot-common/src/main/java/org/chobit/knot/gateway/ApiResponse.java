package org.chobit.knot.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 返回体。成功时 {@code success=true}；业务失败时 {@code success=false}，可选 {@link #code()} 与 zhy rw 风格对齐。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, Integer code, String tags) {

    public ApiResponse {
        tags = (tags == null || tags.isBlank()) ? null : tags;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "ok", data, null, null);
    }

    /** 由 {@link org.chobit.knot.gateway.rw.ApiResponseWrapperAdvice} 使用，写入成功码与标签。 */
    public static <T> ApiResponse<T> ok(T data, int successCode, String tags) {
        return new ApiResponse<>(true, "ok", data, successCode, tags);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null, null, null);
    }

    public static <T> ApiResponse<T> fail(int failCode, String message) {
        return new ApiResponse<>(false, message, null, failCode, null);
    }
}
