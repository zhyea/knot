package org.chobit.knot.gateway.outage;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.common.model.EjectionPolicy;
import org.springframework.stereotype.Component;

/**
 * 摘除的门面：请求链只跟它打交道，不直接碰 {@link TargetOutageStore}。
 *
 * <p>一次候选调用的三段式用法（见 {@code GatewayRequestHandler#proxyWithFailover}）：</p>
 * <ol>
 *   <li>{@link #evaluate}：决定该候选是<b>跳过</b>、<b>作为探活放行</b>还是<b>正常放行</b>；</li>
 *   <li>{@link #recordFailure}：上游故障类失败计入窗口；</li>
 *   <li>{@link #recordSuccess}：成功清零窗口；若本次是探活请求则彻底恢复。</li>
 * </ol>
 *
 * <p>策略未启用时所有方法都是无副作用的短路——摘除是显式 opt-in 的能力。</p>
 */
@Slf4j
@Component
public class OutageTracker {

    private final TargetOutageStore store;
    private final OutageKeys keys;

    /**
     * Constructs a new instance.
     */
    public OutageTracker(TargetOutageStore store, OutageKeys keys) {
        this.store = store;
        this.keys = keys;
    }

    /**
     * 评估当前候选是否可参与本次请求。
     *
     * @param policy     规则级摘除策略
     * @param ruleCode   路由规则业务码
     * @param modelCode  供应商模型业务码（候选展开后的具体模型）
     * @param nowMillis  当前时刻
     */
    public EjectionDecision evaluate(EjectionPolicy policy, String ruleCode, String modelCode, long nowMillis) {
        String key = keys.of(ruleCode, modelCode);
        if (policy == null || !policy.isEnabled()) {
            return new EjectionDecision(key, OutageState.HEALTHY, false, false, false);
        }
        OutageState state = store.state(key, nowMillis);
        if (!state.ejected(nowMillis)) {
            return new EjectionDecision(key, state, false, false, true);
        }
        // 冷却已过 → 抢占探活租约：抢到即放行本次请求作为探活，抢不到（已有他人在探）继续跳过
        boolean probing = store.tryAcquireProbe(key, policy.probeTimeoutMs(), nowMillis);
        return new EjectionDecision(key, state, !probing, probing, true);
    }

    /**
     * 记录一次上游故障类失败；不计入判定的失败（4xx 业务错、配置类错）直接短路。
     */
    public void recordFailure(EjectionDecision decision,
                              EjectionPolicy policy,
                              GatewayUpstreamException error,
                              long nowMillis) {
        if (!decision.tracked() || !OutageClassifier.countable(error, policy)) {
            return;
        }
        OutageState next = store.recordAnomaly(decision.key(), policy, AnomalyKind.FAILURE, nowMillis);
        if (next.ejected(nowMillis) && !decision.state().ejected(nowMillis)) {
            log.warn("Routing target ejected after upstream failures: key={}, anomalies={}, cooldownMs={}, reason={}",
                    decision.key(), next.anomalies(), policy.cooldownMs(), next.reason());
        }
    }

    /**
     * 记录一次成功：探活成功即恢复，普通成功只清零窗口计数。
     */
    public void recordSuccess(EjectionDecision decision, long nowMillis) {
        if (!decision.tracked()) {
            return;
        }
        store.recordSuccess(decision.key(), decision.probing(), nowMillis);
        if (decision.probing()) {
            log.info("Routing target recovered after probe succeeded: key={}", decision.key());
        }
    }

    /**
     * 记录一次慢首 token（TTFT 超阈值）：与失败类异常共用同一套窗口计数器，达阈值同样摘除。
     *
     * <p>由首 token 观察者在上游首个可观测产出到达时回调（见
     * {@code FirstTokenHeaderWriter#mark()}）。三个前置条件任一不满足即短路：
     * 策略未启用、未开 {@code countSlow}、耗时未达 {@code slowFirstTokenMs}——
     * 慢信号是 opt-in 的观测，不能反过来拖慢正常请求。</p>
     *
     * <p>fail-open 轮次（候选全摘后忽略摘除重跑）产生的慢信号同样计数：
     * 目标确实慢，与它当前是否被摘除无关。</p>
     *
     * @param policy        规则级摘除策略（null 或未启用时短路）
     * @param ruleCode      路由规则业务码
     * @param modelCode     本次命中的供应商模型业务码
     * @param elapsedMillis 首 token 端到端耗时（毫秒）
     * @param nowMillis     当前时刻
     */
    public void recordSlow(EjectionPolicy policy,
                           String ruleCode,
                           String modelCode,
                           long elapsedMillis,
                           long nowMillis) {
        if (policy == null || !policy.isEnabled() || !policy.isCountSlow()) {
            return;
        }
        if (elapsedMillis < policy.slowFirstTokenMs()) {
            return;
        }
        String key = keys.of(ruleCode, modelCode);
        OutageState before = store.state(key, nowMillis);
        OutageState next = store.recordAnomaly(key, policy, AnomalyKind.SLOW, nowMillis);
        if (next.ejected(nowMillis) && !before.ejected(nowMillis)) {
            log.warn("Routing target ejected after slow first token: key={}, ttftMs={}, thresholdMs={}, "
                            + "anomalies={}, cooldownMs={}, reason={}",
                    key, elapsedMillis, policy.slowFirstTokenMs(), next.anomalies(), policy.cooldownMs(), next.reason());
        }
    }
}
