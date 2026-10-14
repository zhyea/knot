package org.chobit.knot.gateway.plugin.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 网关路由快照：一次请求命中路由规则后的固定上下文，供插件日志直接携带。
 *
 * <p>原先由 {@code Map<String, Object>} + 字符串键拼装，字段集合虽固定但无法在类型上约束；
 * 这里改为具名类型，字段名由 Java 成员决定。
 *
 * <p>{@code appId} / {@code department} 只在路由信息存在时取值，缺失时序列化中省略该字段
 * （保持与原先「不 put 该 key」一致的 JSON 形状）；其余固定字段即使为 null 也照旧输出，
 * 与原先无条件 put 的行为一致。
 */
public final class GatewayRoutingSnapshot {

    private final Long ruleId;
    private final String ruleCode;
    private final Long consumerId;
    private final boolean returnUsageDetail;
    private final int candidateCount;
    private final String appId;
    private final String department;

    public GatewayRoutingSnapshot(Long ruleId,
                                  String ruleCode,
                                  Long consumerId,
                                  boolean returnUsageDetail,
                                  int candidateCount,
                                  String appId,
                                  String department) {
        this.ruleId = ruleId;
        this.ruleCode = ruleCode;
        this.consumerId = consumerId;
        this.returnUsageDetail = returnUsageDetail;
        this.candidateCount = candidateCount;
        this.appId = appId;
        this.department = department;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Long getConsumerId() {
        return consumerId;
    }

    public boolean isReturnUsageDetail() {
        return returnUsageDetail;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getAppId() {
        return appId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDepartment() {
        return department;
    }
}
