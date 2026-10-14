package org.chobit.knot.gateway.outage;

/**
 * 计入摘除判定的异常种类。
 *
 * <p>两类信号共用同一个窗口计数器，任一达阈值即触发摘除：</p>
 * <ul>
 *   <li>{@link #FAILURE}：上游故障类失败（5xx、上游 429、网络超时/连接错误）；</li>
 *   <li>{@link #SLOW}：请求成功但首 token 延迟超过策略阈值。</li>
 * </ul>
 */
public enum AnomalyKind {

    /** 上游故障类失败 */
    FAILURE,
    /** 首 token 响应过慢（成功路径上的异常） */
    SLOW
}
