package org.chobit.knot.gateway.outage.admin;

/**
 * 手动摘除请求：不依赖窗口计数，直接把「规则 × 模型」打入冷却。
 *
 * @param ruleCode   路由规则业务码
 * @param modelCode  供应商模型业务码
 * @param cooldownSec 冷却时长（秒）；空或非正值按 60s 处理
 * @param reason     人工原因（进 WARN 日志与管理端操作日志，不参与存储状态）
 */
public record OutageAdminEjectRequest(
        String ruleCode,
        String modelCode,
        Integer cooldownSec,
        String reason
) {

    /**
     * 默认冷却时长（秒）：请求未携带 cooldownSec 时使用，与策略默认冷却一致。
     */
    public static final int DEFAULT_COOLDOWN_SEC = 60;

    /**
     * 归一化后的冷却毫秒数：空或非正值回落默认值。
     */
    public long cooldownMillis() {
        if (cooldownSec == null || cooldownSec <= 0) {
            return DEFAULT_COOLDOWN_SEC * 1000L;
        }
        return cooldownSec * 1000L;
    }
}
