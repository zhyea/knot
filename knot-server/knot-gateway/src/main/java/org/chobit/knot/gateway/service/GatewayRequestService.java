package org.chobit.knot.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.*;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class GatewayRequestService extends AbstractGatewayRequestTemplate {

    private final ProxyService proxyService;
    private final RateLimitService rateLimitService;
    private final RoutingAuthService routingAuthService;
    private final GatewayBillingService billingService;
    private final GatewayTrafficGuard trafficGuard;

    /**
     * Constructs a new instance.
     */
    public GatewayRequestService(ProxyService proxyService,
                                 RateLimitService rateLimitService,
                                 RoutingAuthService routingAuthService,
                                 GatewayBillingService billingService,
                                 GatewayTrafficGuard trafficGuard) {
        this.proxyService = proxyService;
        this.rateLimitService = rateLimitService;
        this.routingAuthService = routingAuthService;
        this.billingService = billingService;
        this.trafficGuard = trafficGuard;
    }

    @Override
    protected ResolvedRouting resolveRouting(GatewayRequestContext context) {
        ResolvedRouting routing = routingAuthService.resolveByRule(context.apiKey(), context.ruleCode());
        if (routing == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid API key or routing rule");
        }
        return routing;
    }

    @Override
    protected ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange) {
        return proxyWithFailover(context, exchange);
    }

    private ProxyResult proxyWithFailover(GatewayRequestContext context, GatewayExchange exchange) {
        ResolvedRouting routing = context.routing();
        AppContext appContext = routing.appContext();
        ModelApiProtocolEnum protocol = context.protocol();
        String traceparent = context.traceparent();

        Map<String, Object> requestBody = exchange.requestBody();
        ProxyResult lastResult = null;
        for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
            requestBody.put(AiPayloadFields.MODEL, candidate.targetCode());
            if (isTargetBlocked(routing, appContext, candidate)) {
                return new ProxyResult(null, null, candidate.targetId(),
                        ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED.code(), "Rate limit exceeded");
            }
            lastResult = proxyService.proxy(requestBody, appContext, protocol, traceparent);
            if (lastResult.errorCode() == null) {
                return lastResult;
            }
        }
        return lastResult != null
                ? lastResult
                : new ProxyResult(null, null, null,
                ProxyErrorCodeEnum.NO_ROUTING_TARGET.code(), "No available routing target");
    }

    private boolean isTargetBlocked(ResolvedRouting routing,
                                    AppContext appContext,
                                    RoutingRuleTargetDto candidate) {
        return isRateLimited(appContext, candidate) || !isTargetAllowed(routing, candidate);
    }

    private boolean isRateLimited(AppContext appContext, RoutingRuleTargetDto candidate) {
        return !rateLimitService.checkRateLimit(appContext, candidate.targetCode());
    }

    private boolean isTargetAllowed(ResolvedRouting routing, RoutingRuleTargetDto candidate) {
        return trafficGuard.check(routing, candidate);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange) {
        ProxyResult result = exchange.proxyResult();
        ResolvedRouting routing = context.routing();
        if (routing == null || result == null || result.responseBody() == null) {
            return result == null ? null : result.responseBody();
        }
        if (!routing.returnUsageDetail()) {
            return result.responseBody();
        }
        try {
            Map<String, Object> body = JsonKit.fromJson(result.responseBody(), new TypeReference<>() {
            });
            Object usageObj = body.get(AiPayloadFields.USAGE);
            BillingUsage usage = usageObj instanceof Map<?, ?> map ? BillingUsage.from((Map<String, Object>) map) : BillingUsage.from(Map.of());
            BillingDetail billing = billingService.calculateUsageDetail(result.modelId(), usage);
            if (billing != null) {
                body.put(AiPayloadFields.KNOT_BILLING, billing);
                body.put(AiPayloadFields.KNOT_USAGE, normalizedUsage(usage, billing));
            }
            return body;
        } catch (Exception ignored) {
            return result.responseBody();
        }
    }

    private NormalizedUsage normalizedUsage(BillingUsage usage, BillingDetail billing) {
        long totalTokens = usage.totalTokens() > 0 ? usage.totalTokens() : usage.inputTokens() + usage.outputTokens();
        return new NormalizedUsage(
                totalTokens,
                billing.totalCost(),
                billing.currency(),
                billing.billingRuleId(),
                billing.billingRuleCode(),
                billing.versionNo()
        );
    }
}
