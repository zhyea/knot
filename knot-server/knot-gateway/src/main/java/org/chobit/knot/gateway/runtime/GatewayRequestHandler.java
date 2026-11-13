package org.chobit.knot.gateway.runtime;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskAcceptedResponse;
import org.chobit.knot.gateway.async.AsyncTaskSupport;
import org.chobit.knot.gateway.exception.GatewayQuotaException;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayRequestException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.load.ModelLoadStore;
import org.chobit.knot.gateway.metrics.GatewayMetrics;
import org.chobit.knot.gateway.service.support.ModerationWordResource;
import org.chobit.knot.common.model.EjectionPolicy;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.common.model.RetryPolicy;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.chobit.knot.gateway.outage.EjectionDecision;
import org.chobit.knot.gateway.outage.OutageClassifier;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.OutageState;
import org.chobit.knot.gateway.outage.OutageTracker;
import org.chobit.knot.plugin.PluginDispatcher;
import org.chobit.knot.gateway.retry.BackoffCalculator;
import org.chobit.knot.gateway.retry.RetryableClassifier;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard.TrafficCheckContext;
import org.chobit.knot.gateway.traffic.TrafficDecision;
import org.chobit.knot.gateway.traffic.TrafficUsage;
import org.chobit.knot.gateway.kafka.CallTraceContext;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.gateway.upstream.stream.ProxyStreamingFlux;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

@Slf4j
@Component
public class GatewayRequestHandler extends AbstractGatewayRequestTemplate {

    /**
     * 请求上下文属性：流式响应写完时释放本请求在途计数的回调（{@code Runnable}）。
     */
    private static final String ATTR_LOAD_RELEASE = "loadRelease";

    /**
     * 请求上下文属性：本次候选实际发给上游的 {@code UpstreamRequestContext}，流式补记日志时据此取 provider / model / trace。
     */
    private static final String ATTR_UPSTREAM_CONTEXT = "upstreamContext";

    /** 记账失败日志节流：每 N 次打一条，避免 Redis 故障时把日志打爆 */
    private static final int USAGE_FAILURE_LOG_EVERY = 100;

    /**
     * 请求上下文属性：本次请求实际命中的路由目标（{@code RoutingRuleTargetDto}），候选调用成功后写入。
     * 由首 token 观察者归属慢信号，数仓响应层事件据此取实际承接模型。
     */
    public static final String HIT_TARGET_ATTRIBUTE = "hitTarget";

    /**
     * 请求上下文属性：本次请求实际命中目标的上游完整请求地址（{@code upstreamUrl}），与 {@link #HIT_TARGET_ATTRIBUTE} 同一写入点。
     * 失败路径（无成功候选）不设置，{@code hit} 的 {@code upstreamUrl} 为 null。
     */
    public static final String HIT_TARGET_URL_ATTRIBUTE = "hitTargetUrl";

    /**
     * 请求上下文属性：本次请求成功记账用的流量检查上下文（{@code TrafficCheckContext}）。
     * 非流式在候选成功后同步记账；流式在流写完后由 {@code streamResponse} 的收尾回调补记。
     */
    public static final String TRAFFIC_CONTEXT_ATTRIBUTE = "trafficContext";

    private final UpstreamProxyClient proxyClient;
    private final RoutingResolver routingResolver;
    private final GatewayTrafficGuard trafficGuard;
    private final UsageExtractorRegistry usageExtractorRegistry;
    private final ModelLoadStore modelLoadStore;
    private final ModelLoadKeys modelLoadKeys;
    private final OutageTracker outageTracker;
    private final OutageKeys outageKeys;

    /** 流式用量记账失败计数：配额 / 成本被跳过的次数——必须可计量，不能只靠翻日志。 */
    private final AtomicLong usageRecordFailures = new AtomicLong();
    /**
     * 网关域指标出口（限流 / 额度拒绝、摘除、上游失败、failover、代理耗时）；非 Spring 单测构造本类时为 null，所有埋点判空。
     */
    private final GatewayMetrics metrics;

    /**
     * 阻塞段调度器（{@code gatewayBlockingScheduler}）：上游调用是阻塞契约，必须离开 event loop 执行。
     * 单测构造本类时为 {@code null}，退化为 Reactor 共享的 {@code boundedElastic}。
     */
    private final Scheduler blockingScheduler;

    /**
     * 异步任务支持（提交 + 轮询）；非 Spring 单测构造本类时为 {@code null}，此时全部请求走同步链路。
     */
    private final AsyncTaskSupport asyncTaskSupport;

    /**
     * @param eventPublisher    Kafka 投递器（未开启事件输出时为 {@code null}）
     * @param trafficTopic      流量事件主题全名（埋点业务侧维护，取自 {@code kafka.topic.traffic}）
     * @param blockingScheduler 阻塞段调度器（单测可为 {@code null}）
     * @param asyncTaskSupport  异步任务支持（单测可为 {@code null}）
     */
    public GatewayRequestHandler(UpstreamProxyClient proxyService,
                                 RoutingResolver routingAuthService,
                                 GatewayTrafficGuard trafficGuard,
                                 PluginDispatcher pluginDispatcher,
                                 UsageExtractorRegistry usageExtractorRegistry,
                                 ModelLoadStore modelLoadStore,
                                 ModelLoadKeys modelLoadKeys,
                                 ModerationWordResource moderationWordResource,
                                 OutageTracker outageTracker,
                                 OutageKeys outageKeys,
                                 @Nullable KafkaPublisher eventPublisher,
                                 @Value("${kafka.topic.traffic}") String trafficTopic,
                                 @Nullable GatewayMetrics metrics,
                                 @Nullable Scheduler blockingScheduler,
                                 @Nullable AsyncTaskSupport asyncTaskSupport) {
        super(pluginDispatcher, moderationWordResource, eventPublisher, trafficTopic);
        this.proxyClient = proxyService;
        this.routingResolver = routingAuthService;
        this.trafficGuard = trafficGuard;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.modelLoadStore = modelLoadStore;
        this.modelLoadKeys = modelLoadKeys;
        this.outageTracker = outageTracker;
        this.outageKeys = outageKeys;
        this.metrics = metrics;
        this.blockingScheduler = blockingScheduler;
        this.asyncTaskSupport = asyncTaskSupport;
    }

    @Override
    protected ResolvedRouting resolveRouting(GatewayRequestContext context) {
        return routingResolver.resolveByRule(context.apiKey(), context.ruleCode(), context.stickyKey(), context.protocol());
    }

    @Override
    protected Mono<ProxyResult> proxy(GatewayRequestContext context, GatewayExchange exchange) {
        return proxyWithFailover(context, exchange);
    }

    private Mono<ProxyResult> proxyWithFailover(GatewayRequestContext context, GatewayExchange exchange) {
        ResolvedRouting routing = context.routing();
        // 事件调用轨迹：候选循环里每次尝试都登记，终态时快照进响应层消息
        CallTraceContext callTrace = new CallTraceContext();
        context.putAttribute(CallTraceContext.ATTRIBUTE, callTrace);

        // 检查应用、路由规则和消费者的频控和额度控制
        TrafficDecision routingDecision = trafficGuard.checkRouting(routing);
        if (!routingDecision.allowed()) {
            throw reject(routingDecision);
        }

        Map<String, Object> requestBody = exchange.requestBody();
        EjectionPolicy ejectionPolicy = routing.effectiveEjectionPolicy();
        List<RoutingRuleTargetDto> candidates = routing.candidateModels();
        TrafficCheckContext trafficContext = trafficGuard.newContext();
        return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, false,
            trafficContext, candidates, 0, new ScanState())
            .flatMap(outcome -> {
                if (outcome.result() != null) {
                    return Mono.just(outcome.result());
                }
                if (outcome.hasAllowedTarget()) {
                    return Mono.error(outcome.lastUpstreamException());
                }
                if (outcome.firstRejection() != null) {
                    return Mono.error(reject(outcome.firstRejection()));
                }
                // 候选被摘除一空：摘除是「少打坏目标」的优化，不能演变成整体不可用
                if (outcome.ejectedSkipped() > 0 && ejectionPolicy.isFailOpenWhenAllEjected()) {
                    log.warn("All routing candidates are ejected, fail-open for rule={} (ejected={})",
                        routing.ruleCode(), outcome.ejectedSkipped());
                    callTrace.failOpen();
                    return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, true,
                        trafficGuard.newContext(), candidates, 0, new ScanState())
                        .flatMap(reopened -> {
                            if (reopened.result() != null) {
                                return Mono.just(reopened.result());
                            }
                            if (reopened.firstRejection() != null) {
                                return Mono.error(reject(reopened.firstRejection()));
                            }
                            return Mono.error(reopened.lastUpstreamException());
                        });
                }
                return Mono.error(outcome.lastUpstreamException());
            });
    }

    /**
     * 候选遍历过程中的可变累加状态：跨异步递归调用共享，单次请求内候选顺序遍历，不存在并发竞争。
     */
    private static final class ScanState {
        GatewayUpstreamException lastUpstreamException =
            new GatewayUpstreamException("No available routing target", ProxyErrorCodeEnum.NO_ROUTING_TARGET.code());
        TrafficDecision firstRejection;
        boolean hasAllowedTarget;
        int ejectedSkipped;
    }

    /**
     * 遍历候选链取一次成功结果（异步：整个 failover 链返回 {@link Mono}）。
     * 摘除过滤发生在频控检查之前：被摘除的目标不消耗频控 / 额度预算，也不写入首个拒绝原因；候选按索引顺序递归处理。
     *
     * @param ignoreEjection true 时忽略摘除状态（候选被全部摘除后 fail-open 的第二轮）
     */
    private Mono<FailoverOutcome> attemptCandidates(GatewayRequestContext context,
                                                    GatewayExchange exchange,
                                                    CallTraceContext callTrace,
                                                    Map<String, Object> requestBody,
                                                    EjectionPolicy ejectionPolicy,
                                                    boolean ignoreEjection,
                                                    TrafficCheckContext trafficContext,
                                                    List<RoutingRuleTargetDto> candidates,
                                                    int index,
                                                    ScanState state) {
        if (index >= candidates.size()) {
            return Mono.just(new FailoverOutcome(null, state.lastUpstreamException,
                state.firstRejection, state.hasAllowedTarget, state.ejectedSkipped));
        }
        ResolvedRouting routing = context.routing();
        ModelApiProtocolEnum protocol = context.protocol();
        String traceparent = context.traceparent();
        // 网关自生成的请求标识：每次上游调用都带同一个值（X-Request-Id），重试/failover 不换号——
        // 一个客户端请求对应一个我方编号，供应商侧按它检索才能看到完整重试链
        String requestId = context.requestId();
        RetryPolicy retryPolicy = routing.effectiveRetryPolicy();
        RoutingRuleTargetDto candidate = candidates.get(index);
        EjectionDecision decision = ignoreEjection
            ? new EjectionDecision(outageKeys.of(routing.ruleCode(), candidate.targetCode()),
            OutageState.HEALTHY, false, false, false)
            : outageTracker.evaluate(ejectionPolicy, routing.ruleCode(), candidate.targetCode(),
            System.currentTimeMillis());
        CallTraceContext.AttemptDraft draft = callTrace.newAttempt(candidate, decision);
        if (decision.skip()) {
            state.ejectedSkipped++;
            if (metrics != null) {
                metrics.ejectionSkip();
            }
            return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, ignoreEjection,
                trafficContext, candidates, index + 1, state);
        }
        // 检查目标模型的频控和额度控制
        TrafficDecision targetDecision = trafficGuard.checkTarget(candidate, trafficContext);
        if (!targetDecision.allowed()) {
            draft.rejected(targetDecision.reason() == null ? null : targetDecision.reason().name());
            if (state.firstRejection == null) {
                state.firstRejection = targetDecision;
            }
            return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, ignoreEjection,
                trafficContext, candidates, index + 1, state);
        }
        state.hasAllowedTarget = true;

        // 在途 +1：最空闲选模（LEAST_LOAD）的负载来源，请求结束（含流式写完）才释放
        final String loadKey = modelLoadKeys.model(candidate.targetCode());
        modelLoadStore.enter(loadKey);
        // 释放必须「恰好一次」：leave 是 Redis 计数减法，多减一次等于抹掉别人的在途量，
        // 而重试失败 / 网关自身异常 / 流式收尾三条路径都可能碰到释放点，故用 CAS 收敛。
        final AtomicBoolean loadReleased = new AtomicBoolean(false);
        final Runnable releaseLoad = () -> {
            if (loadReleased.compareAndSet(false, true)) {
                modelLoadStore.leave(loadKey);
            }
        };
        // 流式成功把释放推迟到流写完（见 streamResponse 的 onComplete），置位后 doFinally 不再兜底；
        // 只在 map 正常返回前才置位——中途抛错保持 false，仍由 doFinally 释放，不会漏。
        final AtomicBoolean releaseDeferred = new AtomicBoolean(false);
        Supplier<ProxyResult> attempt = () -> {
            // 每次重试都重建上下文（凭据可能刷新），并把这一版挂到请求属性供流式收尾补记日志
            UpstreamRequestContext upstreamContext = proxyClient.buildRequestContext(
                // 客户端无需传 model：请求体 model 恒按路由目标的上游模型覆盖（kb_models.upstream_model）
                withTargetModel(requestBody, candidate),
                exchange.contentType(), candidate, protocol, traceparent, requestId);
            context.putAttribute(ATTR_UPSTREAM_CONTEXT, upstreamContext);
            return proxyClient.proxy(upstreamContext);
        };
        final long attemptStartedAt = System.nanoTime();
        Mono<ProxyResult> retryMono = invokeWithRetry(retryPolicy, candidate, draft, attempt);
        if (metrics != null) {
            // 代理耗时含该候选内部的退避重试：观测的是「这个候选到底慢不慢」
            retryMono = retryMono
                .doOnNext(ignored -> metrics.proxyLatency(System.nanoTime() - attemptStartedAt, true))
                .doOnError(ignoredEx -> metrics.proxyLatency(System.nanoTime() - attemptStartedAt, false));
        }
        return retryMono
            .map(result -> {
                // 限额与 TPM 事后累计：上游成功才记账，失败重试与 failover 不重复扣减。
                // 异步提交只完成了 create，真实用量还没发生；此时记账等于把创建回执里的占位量
                // 写进额度。终态查询成功后才按实际用量记一次（见 AsyncTaskCompletion#recordUsage）。
                if (asyncTaskSupport == null || !asyncTaskSupport.isAsync(candidate, protocol)) {
                    trafficGuard.record(routing, candidate, usageOf(result), trafficContext);
                }
                outageTracker.recordSuccess(decision, System.currentTimeMillis());
                // 供应商侧请求 id 一并留痕：数仓 attempt 级绑定 + 联合排查检索（不透客户端）
                draft.succeeded(result.upstreamRequestId());
                draft.setUpstreamUrl(result.upstreamUrl());
                if (metrics != null && index > 0) {
                    // 靠后的候选承接成功 = 前面排队的候选不可用，是上游健康度的重要预警信号
                    metrics.failover();
                }
                boolean deferredToStream = false;
                if (result.streamResponse() != null) {
                    // 流式：在途必须持续到响应流写完，交给响应体结束时释放
                    deferredToStream = true;
                    context.putAttribute(ATTR_LOAD_RELEASE, (Runnable) () -> {
                        // 流真正结束时才让兜底重新生效，避免「已登记释放」被 doFinally 提前消费
                        releaseDeferred.set(false);
                        releaseLoad.run();
                    });
                }
                // 响应头回写实际承接模型：流式此时尚未 commit（首行 flush 才提交头），写头依然生效
                writeSelectedModelHeader(candidate, exchange);
                // 命中目标入上下文：首 token 观察者据此把慢信号归属到本候选（异常路径无首 token）
                context.putAttribute(HIT_TARGET_ATTRIBUTE, candidate);
                context.putAttribute(HIT_TARGET_URL_ATTRIBUTE, result.upstreamUrl());
                context.putAttribute(TRAFFIC_CONTEXT_ATTRIBUTE, trafficContext);
                // 置位放在 map 的最后一行：前面任何一步抛错都还能由 doFinally 兜底释放。
                releaseDeferred.set(deferredToStream);
                return new SuccessCandidate(result, deferredToStream);
            })
            // 只吸收上游失败：网关自身异常（鉴权/频控/缺陷）不属于可重试上游失败，直接上抛终止本次请求
            .onErrorResume(GatewayUpstreamException.class, e -> {
                state.lastUpstreamException = e;
                draft.failed(e, OutageClassifier.countable(e, ejectionPolicy));
                // 一次候选调用最多计一次异常：重试内部的单次失败不放大摘除计数
                outageTracker.recordFailure(decision, ejectionPolicy, e, System.currentTimeMillis());
                if (metrics != null) {
                    metrics.upstreamFailure(e.code());
                }
                // 确定性失败（空响应）：换候选只会再空一次，直接终止本次请求。
                // ⚠ 在途计数必须在此释放——返回错误会跳过下面的 switchIfEmpty，那里才是常规释放点
                if (RetryableClassifier.terminal(e)) {
                    releaseLoad.run();
                    return Mono.error(e);
                }
                return Mono.empty();
            })
            .flatMap(success -> {
                if (!success.deferredToStream()) {
                    releaseLoad.run();
                }
                return Mono.just(new FailoverOutcome(success.result(), null, null, true, state.ejectedSkipped));
            })
            // 上游失败（Mono 已被 onErrorResume 转空）：释放在途并继续下一个候选
            .switchIfEmpty(Mono.defer(() -> {
                releaseLoad.run();
                return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, ignoreEjection,
                    trafficContext, candidates, index + 1, state);
            }))
            // 兜底释放：候选链上任何一步抛非 GatewayUpstreamException（例如 trafficGuard.record 撞上
            // Redis 故障、写响应头失败），都不会经过 map / flatMap 的常规释放点；这里保证在途一定归零，
            // 否则 LEAST_LOAD 会带着只增不减的脏负载长期偏斜。releaseLoad 幂等，重复到达也无副作用。
            .doFinally(signal -> {
                if (!releaseDeferred.get()) {
                    releaseLoad.run();
                }
            });
    }

    /**
     * 单个候选成功时的瞬时结果：携带上游响应与是否把在途释放延迟到流式写完（{@code deferredToStream}）。
     */
    private record SuccessCandidate(ProxyResult result, boolean deferredToStream) {
    }

    /**
     * 一次候选遍历的结果：成功结果与各项失败/跳过统计。
     *
     * @param result                成功的上游结果，null 表示本轮无候选成功
     * @param lastUpstreamException 末次上游失败（无成功结果时抛给调用方）
     * @param firstRejection        首个被频控/额度拒绝的判定（无可用候选时的抛出依据）
     * @param hasAllowedTarget      是否存在通过频控检查的候选
     * @param ejectedSkipped        因摘除被跳过的候选数（用于「全摘除 fail-open」判定）
     */
    private record FailoverOutcome(ProxyResult result,
                                   GatewayUpstreamException lastUpstreamException,
                                   TrafficDecision firstRejection,
                                   boolean hasAllowedTarget,
                                   int ejectedSkipped) {
    }

    /**
     * 把实际承接本次请求的模型业务码（{@code kb_models.model_code}）写入响应头 {@code X-Knot-Model}。
     * 取值为真正调用上游的那个模型；写头走 {@link GatewayExchange#http()} 显式入口。
     */
    private void writeSelectedModelHeader(RoutingRuleTargetDto candidate, GatewayExchange exchange) {
        if (exchange == null || candidate == null || candidate.targetCode() == null) {
            return;
        }
        exchange.http().setResponseHeader(GatewayHeaders.X_KNOT_MODEL, candidate.targetCode());
    }

    /**
     * 把请求体 model 覆盖为当前候选的上游模型后原样返回，便于在重试闭包里复用同一个 body。
     */
    private Map<String, Object> withTargetModel(Map<String, Object> requestBody, RoutingRuleTargetDto candidate) {
        requestBody.put(AiPayloadFields.MODEL, candidate.upstreamModelCode());
        return requestBody;
    }

    /**
     * 对同一个路由目标按规则级策略做失败重试（非阻塞异步版）。
     * 重试位于跨候选 failover 之前：同一目标先原地重投，次数耗尽或命中不可重试口径后，由外层切到下一个候选。
     * 只有 {@link GatewayUpstreamException} 是可重试的上游失败，网关自身的异常直接以原异常终止。
     * 退避由 {@code Mono.delay} 承载，延迟期间不占用线程，之后回到阻塞段调度器执行；流式请求只在首个字节之前可重投。
     */
    Mono<ProxyResult> invokeWithRetry(RetryPolicy policy,
                                      RoutingRuleTargetDto candidate,
                                      CallTraceContext.AttemptDraft draft,
                                      Supplier<ProxyResult> attempt) {
        int maxAttempts = Math.max(1, policy.maxAttempts());
        return retryOnce(policy, candidate, draft, attempt, 0, maxAttempts);
    }

    /**
     * 单次尝试 + 退避调度的递归实现（见 {@link #invokeWithRetry} 说明）。
     */
    private Mono<ProxyResult> retryOnce(RetryPolicy policy,
                                        RoutingRuleTargetDto candidate,
                                        CallTraceContext.AttemptDraft draft,
                                        Supplier<ProxyResult> attempt,
                                        int failed,
                                        int maxAttempts) {
        if (draft != null) {
            draft.nextAttempt(maxAttempts);
        }
        // 上游调用是阻塞契约（executor 内部 block），必须在阻塞段调度器执行，不能占用 event loop；
        // 顺带在任务线程上重绑 MDC——本段是整条链路里日志最密集的地方（重试、摘除、计费）
        Mono<ProxyResult> first = MdcContextSupport.onBlockingScheduler(
            Mono.fromCallable(attempt::get), blockingScheduler);
        return first.onErrorResume(GatewayUpstreamException.class, e -> {
            if (failed + 1 >= maxAttempts || !RetryableClassifier.retryable(e, policy)) {
                return Mono.error(e);
            }
            long delayMs = BackoffCalculator.delayMillis(failed + 1, policy, e.retryAfterMs());
            log.warn("Retry routing target after upstream failure: target={}, attempt={}/{}, status={}, code={}, delayMs={}",
                candidate.targetCode(), failed + 1, maxAttempts, e.httpStatus(), e.code(), delayMs);
            // 非阻塞退避：Reactor 定时器到期后回到代理池重试，期间无任何线程被占用
            return Mono.delay(Duration.ofMillis(delayMs))
                .then(retryOnce(policy, candidate, draft, attempt, failed + 1, maxAttempts));
        });
    }

    /**
     * 把流量判定翻译成网关异常：限流与额度共用 429 出口，靠错误码与明细区分处置。
     */
    private GatewayRequestException reject(TrafficDecision decision) {
        TrafficRejectDetail detail = TrafficRejectDetail.of(decision, System.currentTimeMillis());
        String code = decision.reason().errorCode().code();
        if (metrics != null) {
            metrics.trafficReject(decision.reason() == null ? null : decision.reason().name());
        }
        if (decision.reason().quota()) {
            return new GatewayQuotaException(detail.describe(), code, detail);
        }
        return new GatewayRateLimitException(detail.describe(), code, detail);
    }

    /**
     * 本次调用的用量：token 取归一化结果（无计费规则时退回上游用量输入），
     * 成本与币种只认计费结果——没有计费结果就没有成本，不做估算。
     */
    private TrafficUsage usageOf(ProxyResult result) {
        return TrafficUsage.of(result == null ? null : result.usage());
    }


    /**
     * 本次调用是否注入「返回消费详情」（{@code knot_extend}）：消费者配置开启，或请求头
     * {@code X-Knot-Test-Return-Usage: true} 临时开启，二者其一即注入；非流式与流式两个注入点共用。
     */
    private boolean shouldReturnUsageDetail(ResolvedRouting routing, GatewayExchange exchange) {
        return routing != null && (routing.returnUsageDetail() || testReturnUsageRequested(exchange));
    }

    /**
     * 读取管理面测试头。走 {@link GatewayHttpExchange} 的显式入口——本判定可能发生在
     * 异步线程上（流式收尾），读线程本地的请求属性在那里拿不到，会被误判成「未开启」。
     */
    private boolean testReturnUsageRequested(GatewayExchange exchange) {
        if (exchange == null) {
            return false;
        }
        return isTestReturnUsageHeader(exchange.http().requestHeader(GatewayHeaders.X_KNOT_TEST_RETURN_USAGE));
    }

    /**
     * 测试头值解析：仅 {@code true}（忽略大小写与首尾空白）生效，缺失或其他值按未开启。
     * 包级可见供单测直接驱动。
     */
    static boolean isTestReturnUsageHeader(String headerValue) {
        return GatewayHeaders.testReturnUsageRequested(headerValue);
    }

    @Override
    protected Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange) {
        ProxyResult result = exchange.proxyResult();
        ResolvedRouting routing = context.routing();
        if (result == null) {
            return null;
        }
        if (result.streamResponse() != null) {
            return streamResponse(result, routing, context, exchange);
        }
        // 异步任务：本次响应只是「任务已提交」，不是终态。落一条待轮询任务并回 202 信封，
        // 请求线程到此为止——用量与计费等轮询器拿到终态再做。
        // ⭐ 本类不再为这次提交做任何埋点：事件里那条 task 标记由 submitIfAsync 自己写，
        // 与轮询侧的事务同源。请求链路只回答「是不是异步」，不参与事件格式。
        if (asyncTaskSupport != null) {
            AsyncTaskAcceptedResponse accepted = asyncTaskSupport.submitIfAsync(context, exchange, result);
            if (accepted != null) {
                // 提交回执不是消费完成：把代理链路上推出来的占位 usage 摘掉，
                // 否则流量事件会在 task_terminal=false 时输出 billing / usage。
                exchange.proxyResult(result.withoutUsage());
                return accepted;
            }
        }
        if (routing == null || result.responseBody() == null) {
            return result.responseBody();
        }
        if (!shouldReturnUsageDetail(routing, exchange)) {
            return result.responseBody();
        }
        KnotExtendPayload payload = KnotExtendPayload.of(result.usage());
        if (payload == null) {
            return result.responseBody();
        }
        Map<String, Object> rawUsage = result.usage().rawUsage();
        if (rawUsage != null && rawUsage.isEmpty()) {
            rawUsage = null;
        }
        if (isEventStream(result.responseBody())) {
            return appendUsageEvent(result.responseBody(), payload, rawUsage);
        }
        Map<String, Object> body = JsonKit.fromJson(result.responseBody(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        if (body == null) {
            return result.responseBody();
        }
        body.put(AiPayloadFields.KNOT_EXTEND, payload);
        if (rawUsage != null) {
            // 与 SSE 事件、异步任务查询响应同名：原文单独占一个键，不顶掉响应体自带的适配后 usage
            body.put(AiPayloadFields.UPSTREAM_USAGE, rawUsage);
        }
        return body;
    }

    /**
     * 流式转发：先写好响应头，再把上游响应体以 {@code Flux<DataBuffer>} 交给容器写出。
     * 返回 {@link GatewayStreamingResponse}，由 {@code GatewayControllerSupport} 交给 {@code ServerHttpResponse.writeWith(...)}；订阅即转发，客户端断连即取消。
     */
    private GatewayStreamingResponse streamResponse(ProxyResult result, ResolvedRouting routing,
                                                    GatewayRequestContext context, GatewayExchange exchange) {
        UpstreamStreamResponse stream = result.streamResponse();
        exchange.http().prepareStreaming(stream.contentType());
        boolean appendUsage = shouldReturnUsageDetail(routing, exchange);
        // 在途计数在流写完时才释放（见 proxyWithFailover 的 deferredToStream）
        Runnable loadRelease = context.attribute(ATTR_LOAD_RELEASE);
        FirstTokenHeaderWriter firstTokenWriter = FirstTokenHeaderWriter.of(
            context, exchange.http(), firstTokenObserver(context));
        // 流式用量只能在流写完时定型：先落在容器里，由 onComplete 发布流量事件（终态一条）
        AtomicReference<UsageAccounting> terminalUsage = new AtomicReference<>();
        Runnable onComplete = () -> {
            if (loadRelease != null) {
                loadRelease.run();
            }
            // 补记流式真实用量：无论是否接 Kafka，频控/配额账都必须落到计数器
            recordStreamingUsage(context, routing, terminalUsage.get());
        };
        Runnable base = onComplete;
        onComplete = () -> {
            base.run();
            // 走父类的唯一发布出口：投递器判空在那里，本类不必再各判一次；
            // 也让「终态事件从哪发」只有一处可数（非流式 / 终态异常 / 流式收尾）
            publishTraffic(context, exchange, terminalUsage.get(), firstTokenWriter.elapsedMillis(), null);
        };
        // 流式正文只有读完了才有：旁路一份有界原文，流结束时补记上游响应日志
        UpstreamRequestContext upstreamContext = context.attribute(ATTR_UPSTREAM_CONTEXT);
        Consumer<String> streamBodySink = upstreamContext == null ? null
            : tail -> proxyClient.logStreamTail(upstreamContext, result, tail);
        Flux<DataBuffer> body = ProxyStreamingFlux.wrap(stream, usageExtractorRegistry, appendUsage,
            exchange.http().bufferFactory(), firstTokenWriter, terminalUsage::set, onComplete, streamBodySink);
        return new GatewayStreamingResponse(stream.contentType(), body);
    }

    /**
     * 流式响应写完后的补充记账：用流结束时定型的最终用量补全 TPM / token 配额 / 成本配额。
     * 响应已写完，记账异常只记日志、不向上抛。
     */
    /**
     * 流式用量记账失败次数（监控面）：非 0 即意味着有请求绕过了配额 / 成本记账。
     */
    public long usageRecordFailures() {
        return usageRecordFailures.get();
    }

    void recordStreamingUsage(GatewayRequestContext context,
                              ResolvedRouting routing,
                              UsageAccounting accounting) {
        if (accounting == null || routing == null) {
            return;
        }
        RoutingRuleTargetDto candidate = context.attribute(HIT_TARGET_ATTRIBUTE);
        TrafficCheckContext trafficContext = context.attribute(TRAFFIC_CONTEXT_ATTRIBUTE);
        if (candidate == null || trafficContext == null) {
            return;
        }
        try {
            TrafficUsage usage = TrafficUsage.of(accounting);
            trafficGuard.record(routing, candidate, usage, trafficContext);
        } catch (Exception e) {
            // ⚠ 流式响应已提交：这里抛异常只会破坏已开始的响应流，所以不抛。
            // 但「配额 / 频控被静默绕过」是真实风险，必须计数 + 打点让监控能发现。
            long total = usageRecordFailures.incrementAndGet();
            if (metrics != null) {
                // 计数走监控面：日志是采样的（每 100 次一条），告警必须靠单调计数
                metrics.usageRecordFailure();
            }
            if (total % USAGE_FAILURE_LOG_EVERY == 1) {
                log.error("Streaming usage accounting failed, quota/cost skipped: rule={} total={}",
                        routing.ruleCode(), total, e);
            }
        }
    }

    /**
     * 慢信号观察者：策略启用时才挂（未启用则无观察者，TTFT 只写响应头）。
     * 策略与规则码在请求线程捕获，命中目标在观察者触发时从上下文属性读取。
     */
    @Override
    protected LongConsumer firstTokenObserver(GatewayRequestContext context) {
        ResolvedRouting routing = context.routing();
        if (routing == null) {
            return null;
        }
        EjectionPolicy policy = routing.effectiveEjectionPolicy();
        if (policy == null || !policy.isEnabled()) {
            return null;
        }
        return slowSignalObserver(context, outageTracker, policy, routing.ruleCode());
    }

    /**
     * 慢信号观察者工厂：首 token 耗时超过策略阈值时给命中目标记一次 SLOW 异常。
     * 包级可见供单测直接驱动；取不到命中目标（异常路径、非 proxy 出口）时静默跳过。
     */
    static LongConsumer slowSignalObserver(GatewayRequestContext context,
                                           OutageTracker tracker,
                                           EjectionPolicy policy,
                                           String ruleCode) {
        return elapsed -> {
            RoutingRuleTargetDto hit = context.attribute(HIT_TARGET_ATTRIBUTE);
            if (hit == null || hit.targetCode() == null) {
                return;
            }
            tracker.recordSlow(policy, ruleCode, hit.targetCode(), elapsed, System.currentTimeMillis());
        };
    }

    private boolean isEventStream(String value) {
        return value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    /**
     * 在 {@code [DONE]} 之前插入独立的 {@code knot_extend} 事件（上游原始 usage 以并列 {@code upstream_usage} 字段透出），
     * 不改动上游原有 chunk；无 {@code [DONE]} 时追加在流末尾。
     */
    private String appendUsageEvent(String responseBody, KnotExtendPayload payload, Map<String, Object> rawUsage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(AiPayloadFields.KNOT_EXTEND, payload);
        if (rawUsage != null) {
            event.put(AiPayloadFields.UPSTREAM_USAGE, rawUsage);
        }
        String usageData = "data: " + JsonKit.toJson(event);
        String doneMarker = "data: [DONE]";
        int doneIndex = responseBody.lastIndexOf(doneMarker);
        if (doneIndex < 0) {
            return responseBody + System.lineSeparator() + usageData + System.lineSeparator();
        }
        return responseBody.substring(0, doneIndex)
            + usageData
            + System.lineSeparator()
            + responseBody.substring(doneIndex);
    }
}
