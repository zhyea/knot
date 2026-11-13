package org.chobit.knot.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 网关域指标出口（Micrometer / Prometheus）。
 * 只覆盖决策类指标（限流 / 额度 / 摘除 / 重试 / failover）；HTTP 吞吐与延迟由 Spring 自带的 {@code http.server.requests} 提供。
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

    /** 流式记账失败：非 0 即意味着有请求绕过了配额 / 成本记账。 */
    public static final String USAGE_RECORD_FAILURE = "knot.gateway.usage.record.failure";

    /** 单个候选的端到端代理耗时（含其内部重试）。 */
    public static final String PROXY_LATENCY = "knot.gateway.proxy.latency";

    // ── 敏感词库（内容安全）────────────────────────────────────────────────────
    // ⚠ 这一组不由本类 register：它们需要持有 {@code ModerationWordResource} 实例
    // （两个 gauge 是「读实例状态」而非「记事件」），注册点在该类构造器里。
    // 名字登记在此，是为了让「网关有哪些指标」仍能一处看完。

    /** 词库不可用导致 fail-open：非 0 即意味着有请求在「无敏感词过滤」状态下被放行。 */
    public static final String MODERATION_FAIL_OPEN = "knot.gateway.moderation.fail_open";

    /** 当前是否处于 fail-open（0/1）：持续为 1 = 内容安全能力已被摘除。 */
    public static final String MODERATION_FAIL_OPEN_ACTIVE = "knot.gateway.moderation.fail_open.active";

    /** 最近一次成功取词的词条规模：突降可能是词库被清空或读到了空表。 */
    public static final String MODERATION_WORD_COUNT = "knot.gateway.moderation.word.count";

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
     * 记一次流式记账失败。
     * 流式响应已提交，记账异常不上抛；日志按每 100 次一条采样，并提供单调计数供告警。
     */
    public void usageRecordFailure() {
        Counter.builder(USAGE_RECORD_FAILURE)
                .description("流式记账失败次数（非 0 即存在绕过配额/成本的请求）")
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
