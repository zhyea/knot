package org.chobit.knot.gateway.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.billing.GatewayBillingCalculator;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
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
        return routingResolver.resolveByRule(context.apiKey(), context.ruleCode());
    }

    @Override
    protected ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange) {
        return proxyWithFailover(context, exchange);
    }

    private ProxyResult proxyWithFailover(GatewayRequestContext context, GatewayExchange exchange) {
        ResolvedRouting routing = context.routing();
        ModelApiProtocolEnum protocol = context.protocol();
        String traceparent = context.traceparent();

        // 检查应用、路由规则和消费者的频控和额度控制
        if (!trafficGuard.checkRouting(routing)) {
            throw new GatewayRateLimitException("Routing traffic policy rejected", ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED.code());
        }

        Map<String, Object> requestBody = exchange.requestBody();
        TrafficCheckContext trafficContext = trafficGuard.newContext();
        GatewayUpstreamException lastUpstreamException = new GatewayUpstreamException(
                "No available routing target",
                ProxyErrorCodeEnum.NO_ROUTING_TARGET.code()
        );
        boolean hasAllowedTarget = false;
        for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
            // 检查目标模型的频控和额度控制
            if (!trafficGuard.checkTarget(candidate, trafficContext)) {
                continue;
            }
            hasAllowedTarget = true;

            // 替换请求体中的模型代码
            requestBody.put(AiPayloadFields.MODEL, candidate.targetCode());

            // 调用上游服务
            try {
                return proxyClient.proxy(requestBody, protocol, traceparent);
            } catch (GatewayUpstreamException e) {
                lastUpstreamException = e;
            }
        }
        if (!hasAllowedTarget) {
            throw new GatewayRateLimitException("Target traffic policy rejected", ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED.code());
        }
        throw lastUpstreamException;
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
