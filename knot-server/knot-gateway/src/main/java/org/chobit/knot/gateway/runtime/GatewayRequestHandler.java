package org.chobit.knot.gateway.runtime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.exception.GatewayQuotaException;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayRequestException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.load.ModelLoadStore;
import org.chobit.knot.gateway.metrics.GatewayMetrics;
import org.chobit.knot.gateway.service.support.ModerationWordResource;
import org.chobit.knot.gateway.model.EjectionPolicy;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.RetryPolicy;
import org.chobit.knot.gateway.model.TrafficRejectDetail;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.KnotExtendPayload;
import org.chobit.knot.gateway.outage.EjectionDecision;
import org.chobit.knot.gateway.outage.OutageClassifier;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.OutageState;
import org.chobit.knot.gateway.outage.OutageTracker;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
import org.chobit.knot.gateway.retry.BackoffCalculator;
import org.chobit.knot.gateway.retry.RetryableClassifier;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard.TrafficCheckContext;
import org.chobit.knot.gateway.traffic.TrafficDecision;
import org.chobit.knot.gateway.traffic.TrafficUsage;
import org.chobit.knot.gateway.kafka.CallTraceContext;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.gateway.upstream.stream.ProxyStreamingBody;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
     * 请求上下文属性：本次请求实际命中的路由目标（{@code RoutingRuleTargetDto}）。
     *
     * <p>候选调用成功后写入，两处消费：① 首 token 观察者把慢信号归属到具体目标
     * （异常路径无首 token，不产生慢信号）；② 数仓响应层事件据此取实际承接模型。</p>
     */
    public static final String HIT_TARGET_ATTRIBUTE = "hitTarget";

    /**
     * 请求上下文属性：本次请求成功记账用的流量检查上下文（{@code TrafficCheckContext}）。
     *
     * <p>非流式在候选成功后同步记账；流式响应要在流写完后才拿到真实用量，
     * 必须把检查上下文随请求上下文带出，供 {@code streamResponse} 的收尾回调补记。</p>
     */
    public static final String TRAFFIC_CONTEXT_ATTRIBUTE = "trafficContext";

    private final UpstreamProxyClient proxyClient;
    private final RoutingResolver routingResolver;
    private final GatewayTrafficGuard trafficGuard;
    private final UsageExtractorRegistry usageExtractorRegistry;
    private final GatewayUpstreamClientProperties clientProperties;
    private final ModelLoadStore modelLoadStore;
    private final ModelLoadKeys modelLoadKeys;
    private final OutageTracker outageTracker;
    private final OutageKeys outageKeys;
    /**
     * 网关异步请求处理线程池（B1 异步化承载池）。重试退避通过
     * {@link CompletableFuture#delayedExecutor} 把延迟交给定时器，重试动作仍回到本池执行，
     * 退避期间不占用任何线程，彻底消除原先 {@code Thread.sleep} 在异步池线程上的阻塞。
     */
    private final Executor gatewayAsyncExecutor;
    /**
     * 网关域指标出口（限流 / 额度拒绝、摘除、上游失败、failover、代理耗时）。
     * 非 Spring 单元测试构造本类时为 null，所有埋点一律判空——
     * 与 {@code eventPublisher} 同款处理：观测开关不应改变请求链路的结构。
     */
    private final GatewayMetrics metrics;
    /**
     * Kafka 投递器；未开启 Kafka 事件输出（{@code kafka.enabled=false}）时为 {@code null}。
     */
    private final KafkaPublisher eventPublisher;

    /**
     * 流量事件主题全名（埋点业务侧维护；终态发布用，含流式 finally）。
     */
    private final String trafficTopic;

    /**
     * Constructs a new instance.
     *
     * @param eventPublisher Kafka 投递器（未开启事件输出时为 {@code null}）
     * @param trafficTopic   流量事件主题全名（埋点业务侧维护，取自 {@code kafka.topic.traffic}）
     */
    public GatewayRequestHandler(UpstreamProxyClient proxyService,
                                 RoutingResolver routingAuthService,
                                 GatewayTrafficGuard trafficGuard,
                                 PluginDispatcher pluginDispatcher,
                                 UsageExtractorRegistry usageExtractorRegistry,
                                 GatewayUpstreamClientProperties clientProperties,
                                 ModelLoadStore modelLoadStore,
                                 ModelLoadKeys modelLoadKeys,
                                 ModerationWordResource moderationWordResource,
                                 OutageTracker outageTracker,
                                 OutageKeys outageKeys,
                                 @Qualifier("gatewayAsyncExecutor") Executor gatewayAsyncExecutor,
                                 @Nullable KafkaPublisher eventPublisher,
                                 @Value("${kafka.topic.traffic}") String trafficTopic,
                                 @Nullable GatewayMetrics metrics) {
        super(pluginDispatcher, moderationWordResource, eventPublisher, trafficTopic);
        this.proxyClient = proxyService;
        this.routingResolver = routingAuthService;
        this.trafficGuard = trafficGuard;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.clientProperties = clientProperties;
        this.modelLoadStore = modelLoadStore;
        this.modelLoadKeys = modelLoadKeys;
        this.outageTracker = outageTracker;
        this.outageKeys = outageKeys;
        this.gatewayAsyncExecutor = gatewayAsyncExecutor;
        this.eventPublisher = eventPublisher;
        this.trafficTopic = trafficTopic;
        this.metrics = metrics;
    }

    @Override
    protected ResolvedRouting resolveRouting(GatewayRequestContext context) {
        return routingResolver.resolveByRule(context.apiKey(), context.ruleCode(), context.stickyKey(), context.protocol());
    }

    @Override
    protected CompletableFuture<ProxyResult> proxy(GatewayRequestContext context, GatewayExchange exchange, RequestAttributes requestAttributes) {
        return proxyWithFailover(context, exchange, requestAttributes);
    }

    private CompletableFuture<ProxyResult> proxyWithFailover(GatewayRequestContext context, GatewayExchange exchange, RequestAttributes requestAttributes) {
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
                requestAttributes, trafficContext, candidates, 0, new ScanState())
            .thenCompose(outcome -> {
                if (outcome.result() != null) {
                    return CompletableFuture.completedFuture(outcome.result());
                }
                if (outcome.hasAllowedTarget()) {
                    return CompletableFuture.failedFuture(outcome.lastUpstreamException());
                }
                if (outcome.firstRejection() != null) {
                    return CompletableFuture.failedFuture(reject(outcome.firstRejection()));
                }
                // 候选被摘除一空：摘除是「少打坏目标」的优化，不能演变成整体不可用
                if (outcome.ejectedSkipped() > 0 && ejectionPolicy.isFailOpenWhenAllEjected()) {
                    log.warn("All routing candidates are ejected, fail-open for rule={} (ejected={})",
                        routing.ruleCode(), outcome.ejectedSkipped());
                    callTrace.failOpen();
                    return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, true,
                            requestAttributes, trafficGuard.newContext(), candidates, 0, new ScanState())
                        .thenCompose(reopened -> {
                            if (reopened.result() != null) {
                                return CompletableFuture.completedFuture(reopened.result());
                            }
                            if (reopened.firstRejection() != null) {
                                return CompletableFuture.failedFuture(reject(reopened.firstRejection()));
                            }
                            return CompletableFuture.failedFuture(reopened.lastUpstreamException());
                        });
                }
                return CompletableFuture.failedFuture(outcome.lastUpstreamException());
            });
    }

    /**
     * 候选遍历过程中的可变累加状态：跨异步递归调用共享。单次请求内候选是<b>顺序</b>遍历的
     * （一个候选成功/失败后才会进入下一个），因此状态更新不存在并发竞争。
     */
    private static final class ScanState {
        GatewayUpstreamException lastUpstreamException =
            new GatewayUpstreamException("No available routing target", ProxyErrorCodeEnum.NO_ROUTING_TARGET.code());
        TrafficDecision firstRejection;
        boolean hasAllowedTarget;
        int ejectedSkipped;
    }

    /**
     * 遍历候选链取一次成功结果（异步：整个 failover 链返回 {@link CompletableFuture}）。
     *
     * <p>摘除过滤发生在频控检查<b>之前</b>：被摘除的目标既不消耗该目标的频控/额度预算，
     * 也不写入「首个拒绝原因」——被摘除跳过不等于被限流，不能把 429 误报给客户端。</p>
     *
     * <p>候选按索引顺序递归处理：上一个候选成功或彻底失败后才进入下一个，
     * 保证「跨候选 failover」发生在「同候选重试」之后，也保证在途计数与摘除计数不会被并发放大。</p>
     *
     * @param ignoreEjection true 时忽略摘除状态（候选被全部摘除后 fail-open 的第二轮）
     */
    private CompletableFuture<FailoverOutcome> attemptCandidates(GatewayRequestContext context,
                                                                GatewayExchange exchange,
                                                                CallTraceContext callTrace,
                                                                Map<String, Object> requestBody,
                                                                EjectionPolicy ejectionPolicy,
                                                                boolean ignoreEjection,
                                                                RequestAttributes requestAttributes,
                                                                TrafficCheckContext trafficContext,
                                                                List<RoutingRuleTargetDto> candidates,
                                                                int index,
                                                                ScanState state) {
        if (index >= candidates.size()) {
            return CompletableFuture.completedFuture(new FailoverOutcome(null, state.lastUpstreamException,
                state.firstRejection, state.hasAllowedTarget, state.ejectedSkipped));
        }
        ResolvedRouting routing = context.routing();
        ModelApiProtocolEnum protocol = context.protocol();
        String traceparent = context.traceparent();
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
                requestAttributes, trafficContext, candidates, index + 1, state);
        }
        // 检查目标模型的频控和额度控制
        TrafficDecision targetDecision = trafficGuard.checkTarget(candidate, trafficContext);
        if (!targetDecision.allowed()) {
            draft.rejected(targetDecision.reason() == null ? null : targetDecision.reason().name());
            if (state.firstRejection == null) {
                state.firstRejection = targetDecision;
            }
            return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, ignoreEjection,
                requestAttributes, trafficContext, candidates, index + 1, state);
        }
        state.hasAllowedTarget = true;

        // 在途 +1：最空闲选模（LEAST_LOAD）的负载来源，请求结束（含流式写完）才释放
        final String loadKey = modelLoadKeys.model(candidate.targetCode());
        modelLoadStore.enter(loadKey);
        Supplier<ProxyResult> attempt = () -> proxyClient.proxy(
            // 客户端无需传 model：请求体 model 恒按路由目标的上游模型覆盖（kb_models.upstream_model）
            withTargetModel(requestBody, candidate),
            exchange.contentType(), candidate, protocol, traceparent);
        final long attemptStartedAt = System.nanoTime();
        CompletableFuture<ProxyResult> retryFuture = invokeWithRetry(retryPolicy, candidate, draft, attempt, requestAttributes);
        if (metrics != null) {
            // 代理耗时含该候选内部的退避重试：观测的是「这个候选到底慢不慢」
            retryFuture.whenComplete((ignored, ignoredEx) ->
                metrics.proxyLatency(System.nanoTime() - attemptStartedAt, ignoredEx == null));
        }
        return retryFuture
            .thenApply(result -> {
                // 限额与 TPM 事后累计：上游成功才记账，失败重试与 failover 不重复扣减
                trafficGuard.record(routing, candidate, usageOf(result), trafficContext);
                outageTracker.recordSuccess(decision, System.currentTimeMillis());
                // 供应商侧请求 id 一并留痕：数仓 attempt 级绑定 + 联合排查检索（不透客户端）
                draft.succeeded(result.upstreamRequestId());
                if (metrics != null && index > 0) {
                    // 靠后的候选承接成功 = 前面排队的候选不可用，是上游健康度的重要预警信号
                    metrics.failover();
                }
                boolean deferredToStream = false;
                if (result.streamResponse() != null) {
                    // 流式：在途必须持续到响应流写完，交给响应体结束时释放
                    deferredToStream = true;
                    context.putAttribute(ATTR_LOAD_RELEASE, (Runnable) () -> modelLoadStore.leave(loadKey));
                }
                // 响应头回写实际承接模型：流式此时尚未 commit（首行 flush 才提交头），写头依然生效
                writeSelectedModelHeader(candidate);
                // 命中目标入上下文：首 token 观察者据此把慢信号归属到本候选（异常路径无首 token）
                context.putAttribute(HIT_TARGET_ATTRIBUTE, candidate);
                context.putAttribute(TRAFFIC_CONTEXT_ATTRIBUTE, trafficContext);
                return new SuccessCandidate(result, deferredToStream);
            })
            .exceptionally(ex -> {
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
                if (cause instanceof GatewayUpstreamException e) {
                    state.lastUpstreamException = e;
                    draft.failed(e, OutageClassifier.countable(e, ejectionPolicy));
                    // 一次候选调用最多计一次异常：重试内部的单次失败不放大摘除计数
                    outageTracker.recordFailure(decision, ejectionPolicy, e, System.currentTimeMillis());
                    if (metrics != null) {
                        metrics.upstreamFailure(e.code());
                    }
                    return null;
                }
                // 非上游异常（网关自身缺陷/鉴权/频控等）不属于可重试上游失败，直接上抛终止本次请求
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            })
            .thenCompose(success -> {
                if (success != null) {
                    if (!success.deferredToStream()) {
                        modelLoadStore.leave(loadKey);
                    }
                    return CompletableFuture.completedFuture(
                        new FailoverOutcome(success.result(), null, null, true, state.ejectedSkipped));
                }
                // 上游失败：释放在途并继续下一个候选
                modelLoadStore.leave(loadKey);
                return attemptCandidates(context, exchange, callTrace, requestBody, ejectionPolicy, ignoreEjection,
                    requestAttributes, trafficContext, candidates, index + 1, state);
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
     * 把实际承接本次请求的模型业务码（kb_models.model_code）写入响应头 {@code X-Knot-Model}。
     *
     * <p>池内选中、粘性命中、failover 顺延之后真正调用上游的那个模型才是这里的取值，
     * 便于调用方与管理面调试面板定位请求落点。拿不到 response（非 web 场景）时静默跳过。</p>
     */
    private void writeSelectedModelHeader(RoutingRuleTargetDto candidate) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return;
        }
        HttpServletResponse response = servletAttributes.getResponse();
        if (response == null || candidate == null || candidate.targetCode() == null) {
            return;
        }
        response.setHeader(GatewayHeaders.X_KNOT_MODEL, candidate.targetCode());
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
     *
     * <p>重试位于「跨候选 failover」之前：同一目标的连续失败先在原地重投，次数耗尽或
     * 命中不可重试口径时才以最后一个异常完成 future，由外层切到下一个候选。</p>
     *
     * <p>只把 {@link GatewayUpstreamException} 当作可重试的上游失败；网关自身的异常
     * （鉴权、频控、额度、代码缺陷）不属于上游失败，不重试，直接以原异常完成 future。</p>
     *
     * <p><b>非阻塞退避</b>：退避延迟由 {@link CompletableFuture#delayedExecutor} 的定时器承载，
     * 延迟期间<b>不占用任何线程</b>；延迟结束后重试动作回到 {@code gatewayAsyncExecutor} 执行。
     * 这彻底消除了原先 {@code Thread.sleep} 在异步池线程上阻塞、拖垮并发承载的问题。</p>
     *
     * <p>流式请求的失败只可能在「首个字节之前」抛到这里——一旦拿到带
     * {@code streamResponse} 的结果就已向调用方提交，无法再重投，这也正好是流式重试的安全边界。</p>
     */
    CompletableFuture<ProxyResult> invokeWithRetry(RetryPolicy policy,
                                                          RoutingRuleTargetDto candidate,
                                                          CallTraceContext.AttemptDraft draft,
                                                          Supplier<ProxyResult> attempt,
                                                          RequestAttributes requestAttributes) {
        int maxAttempts = Math.max(1, policy.maxAttempts());
        return retryOnce(policy, candidate, draft, attempt, requestAttributes, 0, maxAttempts);
    }

    /**
     * 单次尝试 + 退避调度的递归实现（见 {@link #invokeWithRetry} 说明）。
     */
    private CompletableFuture<ProxyResult> retryOnce(RetryPolicy policy,
                                                    RoutingRuleTargetDto candidate,
                                                    CallTraceContext.AttemptDraft draft,
                                                    Supplier<ProxyResult> attempt,
                                                    RequestAttributes requestAttributes,
                                                    int failed,
                                                    int maxAttempts) {
        if (draft != null) {
            draft.nextAttempt(maxAttempts);
        }
        CompletableFuture<ProxyResult> first = CompletableFuture.supplyAsync(
            () -> withRequestContext(requestAttributes, attempt), gatewayAsyncExecutor);
        CompletableFuture<ProxyResult> result = new CompletableFuture<>();
        first.whenComplete((proxy, ex) -> {
            if (ex == null) {
                result.complete(proxy);
                return;
            }
            Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
            if (!(cause instanceof GatewayUpstreamException e)) {
                result.completeExceptionally(cause);
                return;
            }
            if (failed + 1 >= maxAttempts || !RetryableClassifier.retryable(e, policy)) {
                result.completeExceptionally(e);
                return;
            }
            long delayMs = BackoffCalculator.delayMillis(failed + 1, policy, e.retryAfterMs());
            log.warn("Retry routing target after upstream failure: target={}, attempt={}/{}, status={}, code={}, delayMs={}",
                candidate.targetCode(), failed + 1, maxAttempts, e.httpStatus(), e.code(), delayMs);
            // 非阻塞退避：定时器到期后再回到 gatewayAsyncExecutor 重试，期间无任何线程被占用
            CompletableFuture.completedFuture(null)
                .thenComposeAsync(ignored -> retryOnce(policy, candidate, draft, attempt, requestAttributes, failed + 1, maxAttempts),
                    CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, gatewayAsyncExecutor))
                .whenComplete((next, nextEx) -> {
                    if (nextEx != null) {
                        Throwable nextCause = (nextEx instanceof CompletionException && nextEx.getCause() != null)
                            ? nextEx.getCause() : nextEx;
                        result.completeExceptionally(nextCause);
                    } else {
                        result.complete(next);
                    }
                });
        });
        return result;
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
        UsageAccounting usage = result == null ? null : result.usage();
        if (usage == null) {
            return TrafficUsage.EMPTY;
        }
        NormalizedUsage normalized = usage.normalized();
        return TrafficUsage.of(usage.totalTokens(),
            costOf(normalized),
            normalized == null ? null : normalized.currency());
    }

    /**
     * 计费结果的对外契约是十进制普通字符串（见 {@link NormalizedUsage#totalCost()}），
     * 记账要的是 {@link BigDecimal}，在这里做唯一一次还原；空或非法文本按无成本处理，
     * 与「上游没给 usage.cost」同一口径，不让脏数据炸掉记账链路。
     */
    private static BigDecimal costOf(NormalizedUsage normalized) {
        if (normalized == null || StringUtils.isBlank(normalized.totalCost())) {
            return null;
        }
        try {
            return new BigDecimal(StringUtils.trim(normalized.totalCost()));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }


    /**
     * 本次调用是否注入「返回消费详情」（{@code knot_extend}）：消费者配置开启，
     * 或请求头 {@code X-Knot-Test-Return-Usage: true} 临时开启（管理面调试抽屉用），
     * 二者其一即注入。非流式与流式两个注入点共用同一判定，避免两处口径漂移。
     */
    private boolean shouldReturnUsageDetail(ResolvedRouting routing) {
        return routing != null && (routing.returnUsageDetail() || testReturnUsageRequested());
    }

    /**
     * 读取管理面测试头。与 {@link #writeSelectedModelHeader} 同款请求属性入口
     * （两个决策点都发生在请求线程）；取不到请求（非 web 场景）按未开启处理。
     */
    private boolean testReturnUsageRequested() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return false;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        return request != null && isTestReturnUsageHeader(request.getHeader(GatewayHeaders.X_KNOT_TEST_RETURN_USAGE));
    }

    /**
     * 测试头值解析：仅 {@code true}（忽略大小写与首尾空白）生效，缺失或其他值按未开启。
     * 包级可见供单测直接驱动。
     */
    static boolean isTestReturnUsageHeader(String headerValue) {
        return Boolean.parseBoolean(StringUtils.trimToEmpty(headerValue));
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
        if (routing == null || result.responseBody() == null) {
            return result.responseBody();
        }
        if (!shouldReturnUsageDetail(routing)) {
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
        return body;
    }

    /**
     * 流式转发：先把响应头写好，再把响应体交给 Spring 异步写回。
     *
     * <p>这里必须返回裸的 {@link ProxyStreamingBody}：控制器方法声明的返回类型是 {@code Object}，
     * Spring 依据返回值的运行时类型选择处理器，一旦用 {@code ResponseEntity} 包装就会退化成
     * 消息转换器查找，反而写不出去。</p>
     */
    private Object streamResponse(ProxyResult result, ResolvedRouting routing,
                                  GatewayRequestContext context, GatewayExchange exchange) {
        UpstreamStreamResponse stream = result.streamResponse();
        prepareStreamingHeaders(stream.contentType());
        boolean appendUsage = shouldReturnUsageDetail(routing);
        // 在途计数在流写完时才释放（见 proxyWithFailover 的 deferredToStream）
        Runnable loadRelease = context.attribute(ATTR_LOAD_RELEASE);
        FirstTokenHeaderWriter firstTokenWriter = FirstTokenHeaderWriter.of(context, firstTokenObserver(context));
        // 流式用量只能在流写完时定型：先落在容器里，由 onComplete 发布流量事件（终态一条）
        AtomicReference<UsageAccounting> terminalUsage = new AtomicReference<>();
        Runnable onComplete = () -> {
            if (loadRelease != null) {
                loadRelease.run();
            }
            // 补记流式真实用量：无论是否接 Kafka，频控/配额账都必须落到计数器
            recordStreamingUsage(context, routing, terminalUsage.get());
        };
        if (eventPublisher != null) {
            Runnable base = onComplete;
            onComplete = () -> {
                base.run();
                eventPublisher.publish(trafficTopic, GatewayTrafficMsgDto.of(context, exchange,
                    terminalUsage.get(), firstTokenWriter.elapsedMillis(), null));
            };
        }
        return new ProxyStreamingBody(stream, usageExtractorRegistry, appendUsage,
            clientProperties.getStreamBufferSize(), onComplete, firstTokenWriter, terminalUsage::set);
    }

    /**
     * 流式响应写完后的补充记账：非流式在候选成功时已同步记过一次（当时用量未知、记为空），
     * 这里用流结束时定型的最终用量把 TPM / token 配额 / 成本配额补全。
     *
     * <p>响应已写完，记账异常只记日志、不向上抛，也不影响上面已经完成的在途释放与事件发布。</p>
     */
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
            NormalizedUsage normalized = accounting.normalized();
            TrafficUsage usage = TrafficUsage.of(accounting.totalTokens(),
                costOf(normalized), normalized == null ? null : normalized.currency());
            trafficGuard.record(routing, candidate, usage, trafficContext);
        } catch (Exception e) {
            log.error("Failed to record streaming usage for rule={}", routing.ruleCode(), e);
        }
    }

    /**
     * 慢信号观察者：策略启用时才挂（未启用则无观察者，TTFT 只写响应头）。
     *
     * <p>策略与规则码在请求线程捕获（此刻 {@code context.routing()} 已就绪），
     * 命中目标在观察者触发时（流式可能在异步写线程）从上下文属性惰性读取。</p>
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
     *
     * <p>包级可见供单测直接驱动（无需构造整个 handler）。取不到命中目标
     * （异常路径、非 proxy 出口）时静默跳过——没有首 token 就没有慢信号。</p>
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

    private void prepareStreamingHeaders(MediaType contentType) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return;
        }
        HttpServletResponse response = servletAttributes.getResponse();
        if (response == null) {
            return;
        }
        response.setContentType(contentType == null ? MediaType.TEXT_EVENT_STREAM_VALUE : contentType.toString());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        // 经过 Nginx 等反向代理时不要缓冲 SSE
        response.setHeader("X-Accel-Buffering", "no");
    }

    private boolean isEventStream(String value) {
        return value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    /**
     * 在 {@code [DONE]} 之前插入独立的 {@code knot_extend} 事件（上游原始 usage 以并列 {@code usage} 字段透出），
     * 不改动上游原有 chunk；无 {@code [DONE]} 时追加在流末尾。
     */
    private String appendUsageEvent(String responseBody, KnotExtendPayload payload, Map<String, Object> rawUsage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(AiPayloadFields.KNOT_EXTEND, payload);
        if (rawUsage != null) {
            event.put(AiPayloadFields.RAW_USAGE, rawUsage);
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
