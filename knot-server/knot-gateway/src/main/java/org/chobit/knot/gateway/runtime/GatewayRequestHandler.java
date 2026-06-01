package org.chobit.knot.gateway.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.billing.GatewayBillingCalculator;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.model.*;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard.TrafficCheckContext;
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GatewayRequestHandler extends AbstractGatewayRequestTemplate {

    private final UpstreamProxyClient proxyClient;
    private final RoutingResolver routingResolver;
    private final GatewayBillingCalculator billingCalculator;
    private final GatewayTrafficGuard trafficGuard;

    /**
     * Constructs a new instance.
     */
    public GatewayRequestHandler(UpstreamProxyClient proxyService,
                                 RoutingResolver routingAuthService,
                                 GatewayBillingCalculator billingService,
                                 GatewayTrafficGuard trafficGuard) {
        this.proxyClient = proxyService;
        this.routingResolver = routingAuthService;
        this.billingCalculator = billingService;
        this.trafficGuard = trafficGuard;
    }

    @Override
    protected ResolvedRouting resolveRouting(GatewayRequestContext context) {
        ResolvedRouting routing = routingResolver.resolveByRule(context.apiKey(), context.ruleCode());
        if (routing == null) {
            throw new GatewayAuthException("Invalid API key or routing rule");
        }
        return routing;
    }

    @Override
    protected ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange) {
        return proxyWithFailover(context, exchange);
    }

    private ProxyResult proxyWithFailover(GatewayRequestContext context, GatewayExchange exchange) {
        ResolvedRouting routing = context.routing();
        ModelApiProtocolEnum protocol = context.protocol();
        String traceparent = context.traceparent();

        Map<String, Object> requestBody = exchange.requestBody();
        if (!trafficGuard.checkRouting(routing)) {
            return new ProxyResult(null, null, null,
                    ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED.code(), "Routing traffic policy rejected");
        }

        TrafficCheckContext trafficContext = trafficGuard.newContext();
        ProxyResult lastResult = null;
        for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
            if (isTargetBlocked(candidate, trafficContext)) {
                lastResult = new ProxyResult(null, candidate.providerId(), candidate.targetId(),
                        ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED.code(), "Target traffic policy rejected");
                continue;
            }
            requestBody.put(AiPayloadFields.MODEL, candidate.targetCode());
            lastResult = proxyClient.proxy(requestBody, routing.appContext(), protocol, traceparent);
            if (lastResult.errorCode() == null) {
                return lastResult;
            }
        }
        return lastResult != null
                ? lastResult
                : new ProxyResult(null, null, null,
                ProxyErrorCodeEnum.NO_ROUTING_TARGET.code(), "No available routing target");
    }

    private boolean isTargetBlocked(RoutingRuleTargetDto candidate, TrafficCheckContext trafficContext) {
        return !isTargetAllowed(candidate, trafficContext);
    }

    private boolean isTargetAllowed(RoutingRuleTargetDto candidate, TrafficCheckContext trafficContext) {
        return trafficGuard.checkTarget(candidate, trafficContext);
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
            BillingDetail billing = billingCalculator.calculateUsageDetail(result.modelId(), usage);
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
