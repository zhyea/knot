package org.chobit.knot.gateway.dto.outage;

/**
 * 手动摘除 / 恢复的动作回执（镜像网关侧 {@code OutageAdminAck}）。
 *
 * <p>网关在执行完动作后把生效状态快照回传，admin 后端原样透传给前端，前端无需再查一次列表。</p>
 *
 * @param ruleCode     路由规则业务码
 * @param modelCode    供应商模型业务码
 * @param action       动作标识（{@code eject} / {@code release}）
 * @param ejected      动作后是否处于摘除中
 * @param ejectedUntil 动作后的摘除到期时刻（epoch 毫秒；未摘除为 0）
 * @param reason       生效原因（手动动作为 {@code MANUAL}）
 */
public record GatewayOutageAck(
        String ruleCode,
        String modelCode,
        String action,
        boolean ejected,
        long ejectedUntil,
        String reason
) {
}
