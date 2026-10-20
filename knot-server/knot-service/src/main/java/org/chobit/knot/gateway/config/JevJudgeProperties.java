package org.chobit.knot.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JEV 计费裁判配置（部署级静态配置，非用户可调运行时设置）。
 *
 * <p>对应 v7 设计 §7：不建 {@code ks_system_settings} 表，配置项走 {@code knot.jev.*}，
 * 由管理端读取后经网关 {@code POST /v1/decisions} 调用裁判消费者。配置缺失 / 消费者不存在 /
 * 规则未启用时，裁判区 {@code available:false} + 可执行提示，不炸链路。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.jev")
public class JevJudgeProperties {

    /** 裁判路由规则 code（指向 jev 模型的 DECISIONS 规则） */
    private String judgeRuleCode;
}
