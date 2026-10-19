package org.chobit.knot.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 网关域指标出口（Micrometer / Prometheus）。
 *
 * <p>网关是「限流 / 额度 / 摘除 / 重试 / failover」这些高风险决策的唯一发生地，
 * 而这些决策在正常路径上都是<b>静默</b>的——没有指标就等于线上盲飞：
 * 限流是否误伤、某个上游是否被摘除、重试是否掩盖了故障，全都观测不到。</p>
 *
 * <p>只覆盖决策类指标，HTTP 层面的吞吐与延迟由 Spring 自带的
 * {@code http.server.requests} 提供，两者叠加即为网关的完整可观测面。</p>
 */
@Component
public class GatewayMetrics {

    /** 频控 / 额度拒绝：限流与额度共用 429 出口，靠 {@code reason} 标签区分。 */
    public static final String TRAFFIC_REJECT = "knot.gateway.traffic.reject";

    /** 上游调用失败（一次候选重试全部失败后计一次）。 */
    public static final String UPSTREAM_FAILURE = "knot.gateway.upstream.failure";

    /** 跨候选 failover：靠后的候选承接成功（说明前面的候选不可用）。 */
    public static final String FAILOVER = "knot.gateway.failover";

    /** 候选因异常摘除被跳过。 */
    public static final String EJECTION_SKIP = "knot.gateway.ejection.skip";

    /** 单个候选的端到端代理耗时（含其内部重试）。 */
    public static final String PROXY_LATENCY = "knot.gateway.proxy.latency";

    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记一次频控 / 额度拒绝。
     *
     * @param reason 拒绝原因名（频控 / 额度各自的枚举名）；null 归到 unknown
     */
    public void trafficReject(String reason) {
        Counter.builder(TRAFFIC_REJECT)
                .tag("reason", reason == null ? "unknown" : reason)
                .description("频控与额度拒绝计数（429 出口）")
                .register(registry)
                .increment();
    }

    /**
     * 记一次候选因摘除被跳过：摘除生效频率异常升高通常意味着某个上游在持续劣化。
     */
    public void ejectionSkip() {
        Counter.builder(EJECTION_SKIP)
                .description("因异常摘除被跳过的候选次数")
                .register(registry)
                .increment();
    }

    /**
     * 记一次上游调用失败（同一候选的重试只计一次，不放大故障计数）。
     *
     * @param code 上游错误码（{@code ProxyErrorCodeEnum} 的 code）
     */
    public void upstreamFailure(String code) {
        Counter.builder(UPSTREAM_FAILURE)
                .tag("code", code == null ? "unknown" : code)
                .description("上游调用失败计数（按候选计，不按重投次数计）")
                .register(registry)
                .increment();
    }

    /**
     * 记一次跨候选 failover：靠后的候选承接成功，说明排序靠前的候选不可用。
     */
    public void failover() {
        Counter.builder(FAILOVER)
                .description("跨候选 failover 承接成功的次数")
                .register(registry)
                .increment();
    }

    /**
     * 记一次候选的端到端代理耗时。
     *
     * @param durationNanos 耗时（纳秒），含该候选内部的退避重试
     * @param success       是否成功
     */
    public void proxyLatency(long durationNanos, boolean success) {
        Timer.builder(PROXY_LATENCY)
                .tag("outcome", success ? "success" : "failure")
                .description("单个路由候选的端到端代理耗时（含内部重试退避）")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
