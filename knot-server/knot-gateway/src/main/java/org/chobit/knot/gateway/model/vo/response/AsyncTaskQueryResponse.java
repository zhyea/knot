package org.chobit.knot.gateway.model.vo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.chobit.knot.adapter.usage.KnotExtendPayload;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异步任务查询的<b>响应体</b>（配 HTTP 200）。
 *
 * <p>⭐ <b>为什么是 record 而不是 {@code Map<String, Object>}</b>：这份 body 原先由
 * {@code GatewayTaskController} 用 {@code LinkedHashMap} 逐条 {@code put} 出来，
 * 键名、字段数、嵌套形状全靠人眼——多一个 {@code internal_attempts} 或少一个
 * {@code result_expires_at}，编译与测试都不会响，客户端拿到线上 JSON 才会发现。
 * 换成类型之后，字段集合由组件列表唯一决定，投影逻辑（{@link
 * org.chobit.knot.gateway.async.AsyncTaskLookupRunner}）只能照着声明填，填不进去也漏不掉。</p>
 *
 * <p><b>为什么 {@code result} 仍是 {@code Map}</b>：它是上游产物的<b>动态 payload</b>
 * （视频 URL / 图片数组 / 文本… 由供应商决定形状），属 {@code 10-backend-java.md §5.1}
 * 允许用 Map 的三类场景之一。而 {@code error} 是本项目定义的固定字段集
 * （{@code {code, message}}），所以收成 {@link AsyncTaskFailureResponse}。</p>
 *
 * <p><b>为什么 {@code result} / {@code error} 带 {@code NON_NULL}</b>：这两个键的语义是
 * 「有就出现」——查一个还在途的任务回 {@code "result": null} 会诱导客户端把 null 当结果读。
 * ⚠ 只给这两个字段加，不能加在类上：{@code completed_at} 等在途时恒为 null，
 * 但缺键与值为 null 对客户端是两种解析路径，旧契约一直在发，不许顺手改掉。</p>
 *
 * <p>⚠ <b>与旧 {@code LinkedHashMap} 版本的唯一 wire 差异</b>：{@code result_json} 列字面是
 * {@code "null"} 时（Jackson 反序列化得到 null），旧版 {@code body.put("result", null)}
 * 会输出 {@code "result":null}，本类型则缺键。语义相同（都表示「没有产物」），
 * 但这是一处<b>真实的形状变化</b>，不是「一字节未变」——记在这里，免得下次有人拿
 * 「record 化不改契约」当定律。</p>
 *
 * @param taskCode       knot 自己的任务句柄，对外字段名 {@code task_id}；⚠ 不是上游任务 ID
 * @param status         对外状态，取值见 {@code AsyncTaskLookupRunner#displayStatus}
 *                       （{@code queued/processing/completed/failed/unknown}），
 *                       <b>不是</b>库里的 0~5 状态码
 * @param createdAt      提交时刻，对外字段名 {@code created_at}
 * @param completedAt    终态时刻，在途为 null，对外字段名 {@code completed_at}
 * @param resultExpiresAt 产物保留截止时刻，对外字段名 {@code result_expires_at}
 * @param result         上游产物（已反序列化成对象，不是 JSON 文本），在途/失败为 null
 * @param error          失败信息，非失败为 null
 */
public record AsyncTaskQueryResponse(@JsonProperty("task_id") String taskCode,
                                     String status,
                                     @JsonProperty("created_at") LocalDateTime createdAt,
                                     @JsonProperty("completed_at") LocalDateTime completedAt,
                                     @JsonProperty("result_expires_at") LocalDateTime resultExpiresAt,
                                     @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> result,
                                     @JsonInclude(JsonInclude.Include.NON_NULL) AsyncTaskFailureResponse error,
                                     /**
                                      * 终态结算账目（与同步链路 {@code knot_extend} 同形）；
                                      * 仅当请求带 {@code X-Knot-Test-Return-Usage: true} 且该任务已结算时出现。
                                      *
                                      * <p>⭐ 这是「异步任务的账」唯一的可读出口：它在终态结算时产生，
                                      * 不随提交响应返回（202 信封早于结算），也不写进对外 {@code result}。</p>
                                      */
                                     @JsonInclude(JsonInclude.Include.NON_NULL)
                                     @JsonProperty("knot_extend") KnotExtendPayload knotExtend,
                                     /**
                                      * 上游终态查询响应原文；仅测试头下出现。
                                      *
                                      * <p>用 {@code Map} 而不是类型：它是<b>上游动态 payload</b>，
                                      * 形状由供应商决定（{@code data.task_result.videos[]} 之类），
                                      * 属 {@code 10-backend-java.md §5.1} 允许的三类场景。</p>
                                      */
                                     @JsonInclude(JsonInclude.Include.NON_NULL)
                                     @JsonProperty("upstream_body") Map<String, Object> upstreamBody) {
}
