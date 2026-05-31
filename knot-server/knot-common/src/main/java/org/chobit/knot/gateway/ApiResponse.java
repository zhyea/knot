package org.chobit.knot.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 缁熶竴 API 杩斿洖浣撱€傛垚鍔熸椂 {@code success=true}锛涗笟鍔″け璐ユ椂 {@code success=false}锛屽彲閫?{@link #code()} 涓?zhy rw 椋庢牸瀵归綈銆?
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, Integer code, String tags) {

    /**
     * Executes the public operation. Executes the public operation.
     */
    public ApiResponse {
        tags = (tags == null || tags.isBlank()) ? null : tags;
    }

    /**
     * Executes the public operation.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "ok", data, null, null);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /** 鐢?{@link org.chobit.knot.gateway.rw.ApiResponseWrapperAdvice} 浣跨敤锛屽啓鍏ユ垚鍔熺爜涓庢爣绛俱€?*/
    public static <T> ApiResponse<T> ok(T data, int successCode, String tags) {
        return new ApiResponse<>(true, "ok", data, successCode, tags);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null, null, null);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static <T> ApiResponse<T> fail(int failCode, String message) {
        return new ApiResponse<>(false, message, null, failCode, null);
    }
}
