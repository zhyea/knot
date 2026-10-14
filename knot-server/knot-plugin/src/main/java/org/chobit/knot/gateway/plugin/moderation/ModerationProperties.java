package org.chobit.knot.gateway.plugin.moderation;

import org.chobit.knot.gateway.constants.enums.ModerationActionEnum;
import org.chobit.knot.gateway.constants.enums.ModerationScopeEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 敏感词审核全局配置。<b>只有三项</b>（设计文档 §5.2 口径 A）：其余维度一律走代码常量。
 *
 * <p>全局场景：无资源级策略绑定，所有请求共用这一份配置。</p>
 *
 * <p>三项均未在 {@code application.yml} 声明（值即默认值）；环境变量
 * {@code KNOT_MODERATION_ENABLED / _ACTION / _SCOPE} 经 Spring relaxed binding 仍可覆盖，不依赖 yml 是否声明。</p>
 */
@ConfigurationProperties(prefix = "knot.moderation")
public class ModerationProperties {

    /**
     * 总开关：内容安全会拦生产流量，误杀 / 引擎故障时需要一个明确的关闭入口。
     */
    private boolean enabled = true;

    /**
     * 处置动作：BLOCK / MASK / FLAG（默认 FLAG 影子模式，先观察再收口）。
     */
    private String action = ModerationActionEnum.FLAG.code();

    /**
     * 作用方向：REQUEST / RESPONSE / BOTH。
     */
    private String scope = ModerationScopeEnum.BOTH.code();

    /**
     * Executes the public operation. Executes the public operation.
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * 生效动作：非法配置值回退 FLAG，不阻断启动。
     */
    public ModerationActionEnum actionEnum() {
        return ModerationActionEnum.ofOrDefault(action, ModerationActionEnum.FLAG);
    }

    /**
     * 生效方向：非法配置值回退 BOTH。
     */
    public ModerationScopeEnum scopeEnum() {
        return ModerationScopeEnum.ofOrDefault(scope, ModerationScopeEnum.BOTH);
    }
}
