package org.chobit.knot.gateway.runtime;

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
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.gateway.upstream.stream.ProxyStreamingBody;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
     * 候选调用成功后写入，供首 token 观察者把慢信号归属到具体目标（异常路径无首 token，不产生慢信号）。
     */
    private static final String ATTR_HIT_TARGET = "hitTarget";

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
     * Constructs a new instance.
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
                                 OutageKeys outageKeys) {
        super(pluginDispatcher, moderationWordResource);
        this.proxyClient = proxyService;
        this.routingResolver = routingAuthService;
        this.trafficGuard = trafficGuard;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.clientProperties = clientProperties;
        this.modelLoadStore = modelLoadStore;
        this.modelLoadKeys = modelLoadKeys;
        this.outageTracker = outageTracker;
        this.outageKeys = outageKeys;
    }

    @Override
    protected ResolvedRouting resolveRouting(GatewayRequestContext context) {
        return routingResolver.resolveByRule(context.apiKey(), context.ruleCode(), context.stickyKey(), context.protocol());
    }

    @Override
    protected ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange) {
        return proxyWithFailover(context, exchange);
    }

    private ProxyResult proxyWithFailover(GatewayRequestContext context, GatewayExchange exchange) {
        ResolvedRouting routing = context.routing();

        // 检查应用、路由规则和消费者的频控和额度控制
        TrafficDecision routingDecision = trafficGuard.checkRouting(routing);
        if (!routingDecision.allowed()) {
            throw reject(routingDecision);
        }

        Map<String, Object> requestBody = exchange.requestBody();
        EjectionPolicy ejectionPolicy = routing.effectiveEjectionPolicy();
        FailoverOutcome outcome = attemptCandidates(context, exchange, requestBody, ejectionPolicy, false);
        if (outcome.result() != null) {
            return outcome.result();
        }
        if (outcome.hasAllowedTarget()) {
            throw outcome.lastUpstreamException();
        }
        if (outcome.firstRejection() != null) {
            throw reject(outcome.firstRejection());
        }
        // 候选被摘除一空：摘除是「少打坏目标」的优化，不能演变成整体不可用
        if (outcome.ejectedSkipped() > 0 && ejectionPolicy.isFailOpenWhenAllEjected()) {
            log.warn("All routing candidates are ejected, fail-open for rule={} (ejected={})",
                    routing.ruleCode(), outcome.ejectedSkipped());
            FailoverOutcome reopened = attemptCandidates(context, exchange, requestBody, ejectionPolicy, true);
            if (reopened.result() != null) {
                return reopened.result();
            }
            if (reopened.firstRejection() != null) {
                throw reject(reopened.firstRejection());
            }
            throw reopened.lastUpstreamException();
        }
        throw outcome.lastUpstreamException();
    }

    /**
     * 遍历候选链取一次成功结果。
     *
     * <p>摘除过滤发生在频控检查<b>之前</b>：被摘除的目标既不消耗该目标的频控/额度预算，
     * 也不写入「首个拒绝原因」——被摘除跳过不等于被限流，不能把 429 误报给客户端。</p>
     *
     * @param ignoreEjection true 时忽略摘除状态（候选被全部摘除后 fail-open 的第二轮）
     */
    private FailoverOutcome attemptCandidates(GatewayRequestContext context,
                                              GatewayExchange exchange,
                                              Map<String, Object> requestBody,
                                              EjectionPolicy ejectionPolicy,
                                              boolean ignoreEjection) {
        ResolvedRouting routing = context.routing();
        ModelApiProtocolEnum protocol = context.protocol();
        String traceparent = context.traceparent();
        TrafficCheckContext trafficContext = trafficGuard.newContext();
        GatewayUpstreamException lastUpstreamException = new GatewayUpstreamException("No available routing target",
                ProxyErrorCodeEnum.NO_ROUTING_TARGET.code()
        );
        TrafficDecision firstRejection = null;
        boolean hasAllowedTarget = false;
        int ejectedSkipped = 0;
        RetryPolicy retryPolicy = routing.effectiveRetryPolicy();
        for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
            EjectionDecision decision = ignoreEjection
                    ? new EjectionDecision(outageKeys.of(routing.ruleCode(), candidate.targetCode()),
                    OutageState.HEALTHY, false, false, false)
                    : outageTracker.evaluate(ejectionPolicy, routing.ruleCode(), candidate.targetCode(),
                    System.currentTimeMillis());
            if (decision.skip()) {
                ejectedSkipped++;
                continue;
            }
            // 检查目标模型的频控和额度控制
            TrafficDecision targetDecision = trafficGuard.checkTarget(candidate, trafficContext);
            if (!targetDecision.allowed()) {
                if (firstRejection == null) {
                    firstRejection = targetDecision;
                }
                continue;
            }
            hasAllowedTarget = true;

            // 在途 +1：最空闲选模（LEAST_LOAD）的负载来源，请求结束（含流式写完）才释放
            final String loadKey = modelLoadKeys.model(candidate.targetCode());
            modelLoadStore.enter(loadKey);
            boolean deferredToStream = false;
            // 调用上游服务；失败重试发生在本候选内部，频控检查不随重投次数累加
            try {
                ProxyResult result = invokeWithRetry(retryPolicy, candidate, () -> proxyClient.proxy(
                        // 客户端无需传 model：请求体 model 恒按路由目标的上游模型覆盖（kb_models.upstream_model）
                        withTargetModel(requestBody, candidate),
                        exchange.contentType(), candidate, protocol, traceparent));
                // 限额与 TPM 事后累计：上游成功才记账，失败重试与 failover 不重复扣减
                trafficGuard.record(routing, candidate, usageOf(result), trafficContext);
                outageTracker.recordSuccess(decision, System.currentTimeMillis());
                if (result.streamResponse() != null) {
                    // 流式：请求线程马上返回，在途必须持续到响应流写完，交给响应体结束时释放
                    deferredToStream = true;
                    context.putAttribute(ATTR_LOAD_RELEASE, (Runnable) () -> modelLoadStore.leave(loadKey));
                }
                // 响应头回写实际承接模型：流式此时尚未 commit（首行 flush 才提交头），写头依然生效
                writeSelectedModelHeader(candidate);
                // 命中目标入上下文：首 token 观察者据此把慢信号归属到本候选（异常路径无首 token）
                context.putAttribute(ATTR_HIT_TARGET, candidate);
                return new FailoverOutcome(result, null, null, true, ejectedSkipped);
            } catch (GatewayUpstreamException e) {
                lastUpstreamException = e;
                // 一次候选调用最多计一次异常：重试内部的单次失败不放大摘除计数
                outageTracker.recordFailure(decision, ejectionPolicy, e, System.currentTimeMillis());
            } finally {
                if (!deferredToStream) {
                    modelLoadStore.leave(loadKey);
                }
            }
        }
        return new FailoverOutcome(null, lastUpstreamException, firstRejection, hasAllowedTarget, ejectedSkipped);
    }

    /**
     * 一次候选遍历的结果：成功结果与各项失败/跳过统计。
     *
     * @param result               成功的上游结果，null 表示本轮无候选成功
     * @param lastUpstreamException 末次上游失败（无成功结果时抛给调用方）
     * @param firstRejection       首个被频控/额度拒绝的判定（无可用候选时的抛出依据）
     * @param hasAllowedTarget     是否存在通过频控检查的候选
     * @param ejectedSkipped       因摘除被跳过的候选数（用于「全摘除 fail-open」判定）
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
     * 对同一个路由目标按规则级策略做失败重试。
     *
     * <p>重试位于「跨候选 failover」之前：同一目标的连续失败先在原地重投，次数耗尽或
     * 命中不可重试口径时才抛出最后一个异常，由外层切到下一个候选。</p>
     *
     * <p>只捕获 {@link GatewayUpstreamException}：网关自身的异常（鉴权、频控、额度、代码缺陷）
     * 不属于上游失败，不重试，直接上抛。</p>
     *
     * <p>流式请求的失败只可能在「首个字节之前」抛到这里——一旦拿到带
     * {@code streamResponse} 的结果就已向调用方提交，无法再重投，这也正好是流式重试的安全边界。</p>
     */
    private ProxyResult invokeWithRetry(RetryPolicy policy,
                                        RoutingRuleTargetDto candidate,
                                        Supplier<ProxyResult> attempt) {
        int maxAttempts = Math.max(1, policy.maxAttempts());
        for (int failed = 0; ; failed++) {
            try {
                return attempt.get();
            } catch (GatewayUpstreamException e) {
                if (failed + 1 >= maxAttempts || !RetryableClassifier.retryable(e, policy)) {
                    throw e;
                }
                long delayMs = BackoffCalculator.delayMillis(failed + 1, policy, e.retryAfterMs());
                log.warn("Retry routing target after upstream failure: target={}, attempt={}/{}, status={}, code={}, delayMs={}",
                        candidate.targetCode(), failed + 1, maxAttempts, e.httpStatus(), e.code(), delayMs);
                if (!sleepQuietly(delayMs)) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /**
     * 退避等待；被中断时返回 false，由调用方决定是否中止重试。
     */
    private static boolean sleepQuietly(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 把流量判定翻译成网关异常：限流与额度共用 429 出口，靠错误码与明细区分处置。
     */
    private GatewayRequestException reject(TrafficDecision decision) {
        TrafficRejectDetail detail = TrafficRejectDetail.of(decision, System.currentTimeMillis());
        String code = decision.reason().errorCode().code();
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
                normalized == null ? null : normalized.totalCost(),
                normalized == null ? null : normalized.currency());
    }


    @Override
    protected Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange) {
        ProxyResult result = exchange.proxyResult();
        ResolvedRouting routing = context.routing();
        if (result == null) {
            return null;
        }
        if (result.streamResponse() != null) {
            return streamResponse(result, routing, context);
        }
        if (routing == null || result.responseBody() == null) {
            return result.responseBody();
        }
        if (!routing.returnUsageDetail()) {
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
    private Object streamResponse(ProxyResult result, ResolvedRouting routing, GatewayRequestContext context) {
        UpstreamStreamResponse stream = result.streamResponse();
        prepareStreamingHeaders(stream.contentType());
        boolean appendUsage = routing != null && routing.returnUsageDetail();
        // 在途计数在流写完时才释放（见 proxyWithFailover 的 deferredToStream）
        Runnable loadRelease = context.attribute(ATTR_LOAD_RELEASE);
        return new ProxyStreamingBody(stream, usageExtractorRegistry, appendUsage,
                clientProperties.getStreamBufferSize(), loadRelease,
                FirstTokenHeaderWriter.of(context, firstTokenObserver(context)));
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
            RoutingRuleTargetDto hit = context.attribute(ATTR_HIT_TARGET);
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
