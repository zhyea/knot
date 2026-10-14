package org.chobit.knot.gateway.plugin.moderation;

import org.chobit.knot.gateway.constants.enums.ModerationActionEnum;
import org.chobit.knot.gateway.constants.enums.ModerationScopeEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 敏感词审核全局配置。设计文档 §5.2 口径 A 主张「只有三项」（enabled/action/scope），
 * 其余维度走代码常量。本类额外暴露 <b>refreshInterval</b>，是 robin 在 2026-10-14 复盘后
 * 的显式例外：定时刷新间隔需「全局可配置」，故破例进配置（仍经
 * {@code knot.moderation.refresh-interval} 覆盖，默认值见字段）。
 *
 * <p>全局场景：无资源级策略绑定，所有请求共用这一份配置。</p>
 *
 * <p>未在 {@code application.yml} 声明时取默认值；环境变量
 * {@code KNOT_MODERATION_ENABLED / _ACTION / _SCOPE / _REFRESH_INTERVAL} 经 Spring relaxed binding 仍可覆盖。</p>
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
     * 词库快照定时刷新间隔：绕过 max(updated_at) 版本盲区的主路径（见 {@code ModerationSnapshotCache}）。
     *
     * <p>默认值 10s。删除/停用非最新行时版本号不变、版本轮询无法感知，只能靠这条定时直读词表兜底。
     * 如需更激进可全局调小；调大则最坏生效延迟随之变大。</p>
     */
    private Duration refreshInterval = Duration.ofSeconds(10);

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

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
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
