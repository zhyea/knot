package org.chobit.knot.plugin.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 网关路由快照：一次请求命中路由规则后的固定上下文，供插件日志直接携带。
 *
 * <p>原先由 {@code Map<String, Object>} + 字符串键拼装，字段集合虽固定但无法在类型上约束；
 * 这里改为具名类型，字段名由 Java 成员决定。</p>
 *
 * <p>{@code appId} / {@code department} 只在路由信息存在时取值，缺失时序列化中省略该字段
 * （保持与原先「不 put 该 key」一致的 JSON 形状）；其余固定字段即使为 null 也照旧输出，
 * 与原先无条件 put 的行为一致。</p>
 */
@Getter
@AllArgsConstructor
public final class GatewayRoutingSnapshot {

    private final Long ruleId;
    private final String ruleCode;
    private final Long consumerId;
    private final boolean returnUsageDetail;
    private final int candidateCount;

    /**
     * 仅在路由信息存在时非空；字段级 {@code NON_NULL} 保持「不 put 该 key」的 JSON 形状。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String appId;

    /**
     * 仅在路由信息存在时非空；字段级 {@code NON_NULL} 保持「不 put 该 key」的 JSON 形状。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String department;
}
