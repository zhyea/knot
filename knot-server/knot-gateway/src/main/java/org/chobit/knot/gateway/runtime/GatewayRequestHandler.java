package org.chobit.knot.gateway.runtime;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.exception.GatewayQuotaException;
import org.chobit.knot.gateway.exception.GatewayRateLimitException;
import org.chobit.knot.gateway.exception.GatewayRequestException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.*;
import org.chobit.knot.gateway.model.usage.KnotExtendPayload;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
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

@Component
public class GatewayRequestHandler extends AbstractGatewayRequestTemplate {

    private final UpstreamProxyClient proxyClient;
    private final RoutingResolver routingResolver;
    private final GatewayTrafficGuard trafficGuard;
    private final UsageExtractorRegistry usageExtractorRegistry;
    private final GatewayUpstreamClientProperties clientProperties;

    /**
     * Constructs a new instance.
     */
    public GatewayRequestHandler(UpstreamProxyClient proxyService,
                                 RoutingResolver routingAuthService,
                                 GatewayTrafficGuard trafficGuard,
                                 PluginDispatcher pluginDispatcher,
                                 UsageExtractorRegistry usageExtractorRegistry,
                                 GatewayUpstreamClientProperties clientProperties) {
        super(pluginDispatcher);
        this.proxyClient = proxyService;
        this.routingResolver = routingAuthService;
        this.trafficGuard = trafficGuard;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.clientProperties = clientProperties;
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
        TrafficDecision routingDecision = trafficGuard.checkRouting(routing);
        if (!routingDecision.allowed()) {
            throw reject(routingDecision, "Routing traffic policy rejected");
        }

        Map<String, Object> requestBody = exchange.requestBody();
        TrafficCheckContext trafficContext = trafficGuard.newContext();
        GatewayUpstreamException lastUpstreamException = new GatewayUpstreamException("No available routing target",
                ProxyErrorCodeEnum.NO_ROUTING_TARGET.code()
        );
        TrafficDecision firstRejection = null;
        boolean hasAllowedTarget = false;
        for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
            // 检查目标模型的频控和额度控制
            TrafficDecision targetDecision = trafficGuard.checkTarget(candidate, trafficContext);
            if (!targetDecision.allowed()) {
                if (firstRejection == null) {
                    firstRejection = targetDecision;
                }
                continue;
            }
            hasAllowedTarget = true;

            // 客户端无需传 model：请求体 model 恒按路由目标的上游模型覆盖（kb_models.upstream_model）
            requestBody.put(AiPayloadFields.MODEL, candidate.upstreamModelCode());

            // 调用上游服务
            try {
                ProxyResult result = proxyClient.proxy(requestBody, exchange.contentType(), candidate, protocol, traceparent);
                // 限额与 TPM 事后累计：上游成功才记账，失败重试与 failover 不重复扣减
                trafficGuard.record(routing, candidate, usageOf(result), trafficContext);
                return result;
            } catch (GatewayUpstreamException e) {
                lastUpstreamException = e;
            }
        }
        if (!hasAllowedTarget) {
            if (firstRejection != null) {
                throw reject(firstRejection, "Target traffic policy rejected");
            }
            throw lastUpstreamException;
        }
        throw lastUpstreamException;
    }

    /**
     * 把流量判定翻译成网关异常：频控与额度走不同错误码，便于调用方区分处置。
     */
    private GatewayRequestException reject(TrafficDecision decision, String message) {
        String detail = message + ": resource=" + decision.resourceType() + "#" + decision.resourceId()
                + ", reason=" + decision.reason()
                + ", limit=" + decision.limit() + ", used=" + decision.used();
        if (decision.reason().quota()) {
            return new GatewayQuotaException(detail, decision.reason().errorCode().code());
        }
        return new GatewayRateLimitException(detail, decision.reason().errorCode().code());
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
            return streamResponse(result, routing);
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
        if (isEventStream(result.responseBody())) {
            return appendUsageEvent(result.responseBody(), payload);
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
    private Object streamResponse(ProxyResult result, ResolvedRouting routing) {
        UpstreamStreamResponse stream = result.streamResponse();
        prepareStreamingHeaders(stream.contentType());
        boolean appendUsage = routing != null && routing.returnUsageDetail();
        return new ProxyStreamingBody(stream, usageExtractorRegistry, appendUsage, clientProperties.getStreamBufferSize());
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

    private String appendUsageEvent(String responseBody, KnotExtendPayload payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(AiPayloadFields.KNOT_EXTEND, payload);
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
