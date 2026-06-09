package org.chobit.knot.gateway.runtime;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.billing.GatewayBillingCalculator;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.*;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard.TrafficCheckContext;
import org.chobit.knot.gateway.upstream.UpstreamProxyClient;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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
                                 GatewayTrafficGuard trafficGuard,
                                 PluginDispatcher pluginDispatcher) {
        super(pluginDispatcher);
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
        GatewayUpstreamException lastUpstreamException = new GatewayUpstreamException("No available routing target",
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
                return proxyClient.proxy(requestBody, exchange.contentType(), candidate, protocol, traceparent);
            } catch (GatewayUpstreamException e) {
                lastUpstreamException = e;
            }
        }
        if (!hasAllowedTarget) {
            throw new GatewayRateLimitException("Target traffic policy rejected", ProxyErrorCodeEnum.RATE_LIMIT_EXCEEDED.code());
        }
        throw lastUpstreamException;
    }


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
        BillingDetail billing = billingCalculator.calculateUsageDetail(result.modelId(), result.usage());
        if (billing == null) {
            return result.responseBody();
        }
        NormalizedUsage usage = normalizedUsage(result.usage(), billing);
        if (isEventStream(result.responseBody())) {
            return appendUsageEvent(result.responseBody(), usage);
        }
        Map<String, Object> body = JsonKit.fromJson(result.responseBody(), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        if (body == null) {
            return result.responseBody();
        }
        body.put(AiPayloadFields.KNOT_USAGE, usage);
        return body;
    }

    private NormalizedUsage normalizedUsage(BillingUsage usage, BillingDetail billing) {
        BillingUsage safeUsage = usage == null ? BillingUsage.empty() : usage;
        long totalTokens = safeUsage.totalTokens() > 0 ? safeUsage.totalTokens() : safeUsage.inputTokens() + safeUsage.outputTokens();
        return new NormalizedUsage(
                totalTokens,
                billing.totalCost(),
                billing.currency(),
                billingVersion(billing),
                billing.usageDetails()
        );
    }

    private String billingVersion(BillingDetail billing) {
        if (StringUtils.isNotBlank(billing.versionCode())) {
            return billing.versionCode();
        }
        return billing.versionNo() == null ? null : String.valueOf(billing.versionNo());
    }

    private boolean isEventStream(String value) {
        return value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    private String appendUsageEvent(String responseBody, NormalizedUsage usage) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(AiPayloadFields.KNOT_USAGE, usage);
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
