package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JEV 计费裁判配置（部署级静态配置，非用户可调运行时设置）。
 *
 * <p>对应 v7 设计 §7：不建 {@code ks_system_settings} 表，配置项走 {@code knot.jev.*}，
 * 由管理端读取后经网关 {@code POST /v1/decisions} 调用裁判消费者。配置缺失 / 消费者不存在 /
 * 规则未启用时，裁判区 {@code available:false} + 可执行提示，不炸链路。</p>
 */
@ConfigurationProperties(prefix = "knot.jev")
public class JevJudgeProperties {

    /** 裁判路由规则 code（指向 jev 模型的 DECISIONS 规则） */
    private String judgeRuleCode;

    /** 裁判专用消费者 code（调用 /v1/decisions 时作为 Bearer 凭据来源，凭据不落地、不落日志） */
    private String judgeConsumerCode;

    /**
     * 上游 provider 白名单：仅允许该 provider 承接裁判，避免回退到普通聊天模型导致 answers 形态崩坏。
     * 换供应商时改此配置即可，无需动代码。
     */
    private String providerOnly = "TypeSafe";

    public String getJudgeRuleCode() {
        return judgeRuleCode;
    }

    public void setJudgeRuleCode(String judgeRuleCode) {
        this.judgeRuleCode = judgeRuleCode;
    }

    public String getJudgeConsumerCode() {
        return judgeConsumerCode;
    }

    public void setJudgeConsumerCode(String judgeConsumerCode) {
        this.judgeConsumerCode = judgeConsumerCode;
    }

    public String getProviderOnly() {
        return providerOnly;
    }

    public void setProviderOnly(String providerOnly) {
        this.providerOnly = providerOnly;
    }
}
