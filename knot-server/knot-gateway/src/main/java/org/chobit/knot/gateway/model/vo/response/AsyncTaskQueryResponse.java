package org.chobit.knot.gateway.model.vo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.chobit.knot.adapter.usage.KnotExtendPayload;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异步任务查询的响应体（配 HTTP 200）。
 * {@code result} 是上游产物的动态 payload（形状由供应商决定），用 {@code Map} 承载；{@code error} 收成 {@link AsyncTaskFailureResponse}。
 * {@code result} / {@code error} 带 {@code NON_NULL}（有就出现），只加这两个字段、不加在类上。
 *
 * @param taskCode       knot 自己的任务句柄，对外字段名 {@code task_id}；不是上游任务 ID
 * @param status         {@code kb_async_tasks.status} 的原值，即 {@link org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum} 的码（0 待处理 / 1 轮询中 / 2 成功 / 3 失败 / 4 已超时 / 5 已取消）；是码不是文案，与 {@code /api/enum-options} 同源，label 由调用方按码查
 * @param createdAt      提交时刻，对外字段名 {@code created_at}
 * @param completedAt    终态时刻，在途为 null，对外字段名 {@code completed_at}
 * @param resultExpiresAt 产物保留截止时刻，对外字段名 {@code result_expires_at}
 * @param result         上游产物（已反序列化成对象，不是 JSON 文本），在途/失败为 null
 * @param error          失败信息，非失败为 null
 */
public record AsyncTaskQueryResponse(@JsonProperty("task_id") String taskCode,
                                     Integer status,
                                     @JsonProperty("created_at") LocalDateTime createdAt,
                                     @JsonProperty("completed_at") LocalDateTime completedAt,
                                     @JsonProperty("result_expires_at") LocalDateTime resultExpiresAt,
                                     @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> result,
                                     @JsonInclude(JsonInclude.Include.NON_NULL) AsyncTaskFailureResponse error,
                                     /**
                                      * 终态结算账目（与同步链路 {@code knot_extend} 同形）；任务已到终态且消费者 {@code return_usage_detail} 开启时出现；测试头可叠加触发。
                                      * 在终态结算时产生，不随提交响应返回，也不写进对外 {@code result}。
                                      */
                                     @JsonInclude(JsonInclude.Include.NON_NULL)
                                     @JsonProperty("knot_extend") KnotExtendPayload knotExtend,
                                     /**
                                      * 上游原始 {@code usage} 原文，与 {@code knot_extend} 并列成对输出
                                      * （同步链路同口径，字段名取自 {@code AiPayloadFields#RAW_USAGE}）。
                                      * 异步侧从 {@code terminal_body} 按档案 {@code usage_path} 取对象，
                                      * 未配置时回落顶层 {@code usage} / {@code response.usage}；
                                      * 上游未上报、任务未结算或账目不存在时省略。
                                      */
                                     @JsonInclude(JsonInclude.Include.NON_NULL)
                                     @JsonProperty("usage") Map<String, Object> rawUsage,
                                     /**
                                      * 上游终态查询响应原文；仅测试头下出现。
                                      * 用 {@code Map} 承载上游动态 payload（形状由供应商决定，如 {@code data.task_result.videos[]}）。
                                      */
                                     @JsonInclude(JsonInclude.Include.NON_NULL)
                                     @JsonProperty("upstream_body") Map<String, Object> upstreamBody) {
}
