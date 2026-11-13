package org.chobit.knot.gateway.model.vo.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;

/**
 * 异步任务提交被接受时的响应体（配 HTTP 202），是提交回执而非结果。
 * {@code task_id} / {@code query_url} 为蛇形 wire 名，Java 侧驼峰，靠 {@link JsonProperty} 钉住。
 *
 * @param taskCode knot 生成的任务句柄（32 位 hex），对外字段名 {@code task_id}；不是上游任务 ID，上游 ID 不外泄
 * @param status   任务状态码，提交成功恒为 {@link AsyncTaskStatusEnum#PENDING}（0），是码不是文案，与轮询响应的 {@code status} 同一套
 * @param queryUrl 轮询地址，由 {@link #TASKS_PATH} 拼出，与 {@code GatewayTaskController} 同源；客户端须用 {@code POST} 请求（无请求体，句柄在路径上），{@code GET} 会 405
 */
public record AsyncTaskAcceptedResponse(@JsonProperty("task_id") String taskCode,
                                       Integer status,
                                       @JsonProperty("query_url") String queryUrl) {

    /**
     * 客户端任务端点的路径前缀。
     * {@code query_url} 与 {@code GatewayTaskController} 的映射同源，控制器直接引用该编译期常量。
     */
    public static final String TASKS_PATH = "/openai/v1/tasks";

    /** 提交成功回执：三字段的取值关系固定，调用方不需要（也不应该）自己拼 */
    public static AsyncTaskAcceptedResponse accepted(String taskCode) {
        return new AsyncTaskAcceptedResponse(taskCode, AsyncTaskStatusEnum.PENDING.code(),
                TASKS_PATH + "/" + taskCode);
    }
}
