package org.chobit.knot.gateway.outage;

/**
 * 管理面用的一条摘除条目（键 + 状态），由 {@link TargetOutageStore#list(long)} 产出。
 */
public record OutageEntry(
        /** 路由规则业务码 */
        String ruleCode,
        /** 供应商模型业务码 */
        String modelCode,
        /** 该条目的当前状态 */
        OutageState state
) {

    /**
     * 是否处于摘除中（含探活中——探活期间该目标同样不计入常规候选）。
     */
    public boolean ejected(long nowMillis) {
        return state.ejected(nowMillis) || state.probing(nowMillis);
    }
}
