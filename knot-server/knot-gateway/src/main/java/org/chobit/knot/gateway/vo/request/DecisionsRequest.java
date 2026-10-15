package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Decisions 协议请求（OpenRouter AlphaDecisions：{@code POST /api/alpha/decisions}）。
 *
 * <p>协议形态与对话类完全不同：入参是「待评估内容 {@code state}」+「一组待判定问题
 * {@code questions}」，出参是问题名到答案的一一映射，不做文本生成。</p>
 *
 * <p>{@code questions} 的值是四类多态变体（{@code noul} / {@code choice} / {@code score} /
 * {@code box}），判别键为 {@code type}，且 {@code criteria} 是开放键结构（choice 为任意选项名
 * 到说明的映射，score 为有序说明数组）。网关只做转发与用量提取、不消费问题语义，
 * 因此此处不做变体强类型建模，原样承载避免丢字段；未知字段由基类
 * {@link GatewayModelRequest} 的 {@code extra} 兜底透传。</p>
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
