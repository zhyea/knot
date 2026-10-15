package org.chobit.knot.gateway.outage.admin;

/**
 * 手动摘除 / 恢复的动作回执：把生效后的状态快照带回给管理端，前端无需再查一次列表。
 *
 * @param ruleCode     路由规则业务码
 * @param modelCode    供应商模型业务码
 * @param action       动作标识（{@code eject} / {@code release}）
 * @param ejected      动作后是否处于摘除中
 * @param ejectedUntil 动作后的摘除到期时刻（epoch 毫秒；未摘除为 0）
 * @param reason       生效原因（手动动作为 {@link org.chobit.knot.gateway.outage.OutageState#REASON_MANUAL}）
 */
public record OutageAdminAck(
        String ruleCode,
        String modelCode,
        String action,
        boolean ejected,
        long ejectedUntil,
        String reason
) {

    private static final String ACTION_EJECT = "eject";
    private static final String ACTION_RELEASE = "release";

    /**
     * 手动摘除生效后的回执。
     */
    public static OutageAdminAck ejected(String ruleCode, String modelCode, long ejectedUntil) {
        return new OutageAdminAck(ruleCode, modelCode, ACTION_EJECT, true, ejectedUntil,
                org.chobit.knot.gateway.outage.OutageState.REASON_MANUAL);
    }

    /**
     * 手动恢复生效后的回执。
     */
    public static OutageAdminAck released(String ruleCode, String modelCode) {
        return new OutageAdminAck(ruleCode, modelCode, ACTION_RELEASE, false, 0L, "");
    }
}
