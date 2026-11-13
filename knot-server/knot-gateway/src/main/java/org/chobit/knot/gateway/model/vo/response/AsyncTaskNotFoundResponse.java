package org.chobit.knot.gateway.model.vo.response;

/**
 * 任务查不到时的响应体（配 HTTP 404）。
 * {@code error} 为字符串（如 {@code {"error":"task not found: tc-1"}}），而非统一错误体的对象；「查不到」与「不属于你」共用同一份 404 响应。
 *
 * @param error 面向调用方的短句，含被查询的句柄（便于联调时对照日志）
 */
public record AsyncTaskNotFoundResponse(String error) {

    /**
     * 标准文案：句柄原样回显，客户端据此确认自己查的是哪一个。
     * 空句柄也走这里（{@code task not found: }），统一返回 404。
     */
    public static AsyncTaskNotFoundResponse of(String taskCode) {
        return new AsyncTaskNotFoundResponse("task not found: " + taskCode);
    }
}
