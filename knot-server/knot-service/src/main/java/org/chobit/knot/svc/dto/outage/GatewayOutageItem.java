package org.chobit.knot.svc.dto.outage;

/**
 * 网关摘除管理面的一条条目（镜像网关侧 {@code OutageAdminItem}）。
 *
 * <p>由 admin 后端经 RestClient 透传网关 {@code GET /_admin/outages} 的 JSON 反序列化得到，
 * 字段命名与网关保持一致，前端面板直接按字段渲染。</p>
 *
 * @param ruleCode     路由规则业务码
 * @param modelCode    供应商模型业务码
 * @param failures     窗口内失败类异常计数
 * @param slows        窗口内慢响应计数
 * @param windowEndAt  当前计数窗口结束时刻（epoch 毫秒）
 * @param ejectedUntil 摘除到期时刻（epoch 毫秒；大于当前时刻即摘除中）
 * @param probeUntil   探活租约到期时刻（epoch 毫秒；大于当前时刻即有探活在途）
 * @param cooldownMs   当前生效的冷却时长（探活失败按倍数增长后的值）
 * @param reason       最近一次摘除原因（FAILURE / SLOW / MANUAL）
 */
public record GatewayOutageItem(
        String ruleCode,
        String modelCode,
        int failures,
        int slows,
        long windowEndAt,
        long ejectedUntil,
        long probeUntil,
        long cooldownMs,
        String reason
) {
}
