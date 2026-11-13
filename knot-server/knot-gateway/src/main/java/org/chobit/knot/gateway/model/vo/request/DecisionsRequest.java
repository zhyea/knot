package org.chobit.knot.gateway.model.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Decisions 协议请求（OpenRouter AlphaDecisions：{@code POST /api/alpha/decisions}）。
 * 入参是待评估内容 {@code state} 与一组待判定问题 {@code questions}，出参是问题名到答案的映射，不做文本生成。
 * {@code questions} 为四类变体（{@code noul} / {@code choice} / {@code score} / {@code box}），判别键 {@code type}，{@code criteria} 为开放键结构；原样承载，未知字段由 {@link GatewayModelRequest} 的 {@code extra} 透传。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionsRequest extends GatewayModelRequest {

    /** 问题名 -> 问题定义（含 {@code type} 判别键），必填 */
    public Map<String, Object> questions;

    /** 待评估内容：纯字符串、JSON 对象或数组均可，必填 */
    public Object state;

    /** 上游 provider 路由偏好，透传 */
    public Object provider;

    /** 关联请求分组标识（≤256），上游字段为蛇形 {@code session_id} */
    @JsonProperty("session_id")
    public String sessionId;

    /** 可观测性 / tracing 元数据，透传 */
    public Object trace;

    /** 终端用户标识（≤256），透传 */
    public String user;
}
