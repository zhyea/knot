package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.AuthConstants;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.service.support.ModerationWordResource;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.plugin.PluginDispatcher;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.chobit.knot.gateway.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.gateway.plugin.gateway.GatewayRoutingSnapshot;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.kafka.CallTraceContext;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.gateway.kafka.msg.RequestMsgDto;
import org.chobit.knot.gateway.kafka.msg.ResponseMsgDto;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Handles the incoming request flow. Executes the public operation.
 */
public abstract class AbstractGatewayRequestTemplate {

    private final PluginDispatcher pluginDispatcher;

    /**
     * 词库资源供给（网关唯一的审核职责）：每请求取全量启用词条随插件上下文下发，
     * 列表由 {@link ModerationWordResource} 按路由数据同款节奏缓存（约 30s 生效延迟），
     * 编译 / 去重 / 定时重建已收敛到 knot-plugin 的 {@code ModerationWordCache}。
     */
    private final ModerationWordResource moderationWordResource;

    /**
     * Kafka 投递器；<b>未开启 Kafka 事件输出时为 {@code null}</b>（{@code kafka.enabled=false}）。
     *
     * <p>埋点因此一律判空——旁路观测的开关不应改变请求链路的结构，
     * 关闭时只是「不发布」，不是「走另一条链路」。</p>
     *
     * <p>主题常量由本类（埋点业务侧）维护， publish 时显式传入——投递器是纯管道，
     * 不认识「请求层 / 响应层」这类业务概念。</p>
     */
    private final KafkaPublisher eventPublisher;

    /**
     * 请求层主题全名（埋点业务侧维护，取自 {@code kafka.topic-request}）。
     */
    private final String requestTopic;

    /**
     * 响应层主题全名（埋点业务侧维护，取自 {@code kafka.topic-response}）。
     */
    private final String responseTopic;

    protected AbstractGatewayRequestTemplate(PluginDispatcher pluginDispatcher,
                                             ModerationWordResource moderationWordResource,
                                             KafkaPublisher eventPublisher,
                                             String requestTopic,
                                             String responseTopic) {
        this.pluginDispatcher = pluginDispatcher;
        this.moderationWordResource = moderationWordResource;
        this.eventPublisher = eventPublisher;
        this.requestTopic = requestTopic;
        this.responseTopic = responseTopic;
    }

    /**
     * Gateway request template entrypoint:
     * validate required headers,
     * resolve routing,
     * proxy the adapted request upstream, then apply post-response usage accounting.
     */
    public final Object handle(String authorization,
                               String ruleCode,
                               String traceparent,
                               String stickyKey,
                               GatewayModelRequest request,
                               ModelApiProtocolEnum protocol) {
        return handleMap(authorization, ruleCode, traceparent, stickyKey,
                JsonKit.toMap(request), protocol, MediaType.APPLICATION_JSON);
    }

    /**
     * Gateway request template entrypoint for multipart requests.
     */
    public final Object handleMultipart(String authorization,
                                        String ruleCode,
                                        String traceparent,
                                        String stickyKey,
                                        Map<String, Object> requestBody,
                                        ModelApiProtocolEnum protocol) {
        return handleMap(authorization, ruleCode, traceparent, stickyKey,
                requestBody, protocol, MediaType.MULTIPART_FORM_DATA);
    }

    private Object handleMap(String authorization,
                             String ruleCode,
                             String traceparent,
                             String stickyKey,
                             Map<String, Object> requestBody,
                             ModelApiProtocolEnum protocol,
                             MediaType contentType) {
        // 上下文与载体先建出来，好让「入口即拒」也有一条响应层事件
        // （头校验失败时不发，数仓就统计不到 401/400 的拒绝量）
        GatewayTraceContext traceContext = GatewayTraceContext.currentOrCreate(traceparent);
        GatewayRequestContext context = new GatewayRequestContext(
                authorization, ruleCode, traceContext, protocol, stickyKey);
        GatewayExchange exchange = new GatewayExchange(requestBody, contentType);
        try {
            context.apiKey(validateRequestHeaders(authorization, ruleCode));
            ResolvedRouting routingInfo = resolveRouting(context);
            context.routing(routingInfo);
            // 请求层：此刻「谁在调、走哪条规则、落到哪个应用/部门/用户」已定，结果一个都不知道
            publishRequest(context, exchange, routingInfo);
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_REQUEST, null, null);

            ProxyResult result = proxy(context, exchange);
            exchange.proxyResult(result);
            Object response = applyUsageAccounting(context, exchange);
            // 首 token 响应时间：非流式在此写头（响应体就绪即产出就绪，等价首 token）；
            // 流式返回 StreamingResponseBody，由响应体在上游首行写出前自行写头。
            // 观察者由子类按需提供（如慢信号摘除），两个出口共用同一钩子
            FirstTokenHeaderWriter firstTokenWriter = null;
            if (response != null && !(response instanceof StreamingResponseBody)) {
                firstTokenWriter = FirstTokenHeaderWriter.of(context, firstTokenObserver(context));
                firstTokenWriter.mark();
            }
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_RESPONSE, response, null);
            // 流式不包括在此发布：响应体尚未写完，用量要等 ProxyStreamingBody 收尾才知道，
            // 改由 GatewayRequestHandler 在流结束时发布（见 streamResponse）。
            if (firstTokenWriter != null) {
                ProxyResult proxied = exchange.proxyResult();
                // 非流式：响应体已就绪，用量在此就是终态
                publishResponse(context, proxied == null ? null : proxied.usage(),
                        firstTokenWriter.elapsedMillis(), null);
            }
            return response;
        } catch (RuntimeException ex) {
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_ERROR, null, ex);
            publishResponse(context, null, null, ex);
            throw ex;
        }
    }

    /**
     * 发布请求层消息：数仓输出的旁路开关关闭时整体跳过。
     */
    private void publishRequest(GatewayRequestContext context,
                                GatewayExchange exchange,
                                ResolvedRouting routing) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publish(requestTopic,
                RequestMsgDto.of(context, routing, exchange));
    }

    /**
     * 发布响应层消息（失败路径携带异常 → 统一 error 结构）。
     */
    private void publishResponse(GatewayRequestContext context,
                                 UsageAccounting usage,
                                 Long ttftMs,
                                 Throwable error) {
        if (eventPublisher == null) {
            return;
        }
        CallTraceContext trace = context.attribute(CallTraceContext.ATTRIBUTE);
        eventPublisher.publish(responseTopic,
                ResponseMsgDto.of(context, trace, ttftMs, usage, error));
    }


    protected abstract ResolvedRouting resolveRouting(GatewayRequestContext context);

    protected abstract ProxyResult proxy(GatewayRequestContext context, GatewayExchange exchange);

    protected abstract Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange);

    /**
     * 首 token 观察者钩子：把「首个可观测产出的耗时（毫秒）」暴露给子类的派生能力
     * （如异常摘除的慢信号计数）。默认无观察者；返回非 null 时，
     * {@link FirstTokenHeaderWriter#mark()} 首次触发即以耗时回调一次（先于写头）。
     *
     * <p>流式与非流式两个出口共用本钩子：流式由 {@code applyUsageAccounting} 构造
     * 写头器时取用，非流式由模板在响应体就绪时取用。</p>
     */
    protected LongConsumer firstTokenObserver(GatewayRequestContext context) {
        return null;
    }

    /**
     * Validates header values after Spring MVC has checked required header presence.
     */
    private String validateRequestHeaders(String authorization, String ruleCode) {
        String apiKey = extractApiKey(authorization);
        if (apiKey == null) {
            throw new GatewayAuthException("Invalid API key");
        }
        if (StringUtils.isBlank(ruleCode)) {
            throw new GatewayInvalidRequestException("Rule header must not be blank");
        }
        return apiKey;
    }

    private String extractApiKey(String authorization) {
        if (!StringUtils.startsWith(authorization, AuthConstants.BEARER_PREFIX)) {
            return null;
        }
        return StringUtils.trimToNull(authorization.substring(AuthConstants.BEARER_PREFIX.length()));
    }

    private void dispatchGatewayPlugin(GatewayRequestContext context,
                                       GatewayExchange exchange,
                                       PluginStageCode stageCode,
                                       Object responseBody,
                                       Throwable error) {
        if (pluginDispatcher == null) {
            return;
        }
        pluginDispatcher.dispatch(
                PluginExtensionPoint.GATEWAY_EXCHANGE,
                stageCode,
                new GatewayPluginContext(
                        context.traceId(),
                        context.traceparent(),
                        stageCode,
                        context.apiKey(),
                        context.ruleCode(),
                        context.protocol() == null ? null : context.protocol().canonical().code(),
                        routingSnapshot(context.routing()),
                        moderationWordResource.currentWords(),
                        exchange == null ? null : exchange.requestBody(),
                        exchange == null ? null : exchange.contentType(),
                        responseBody,
                        error
                )
        );
    }

    private GatewayRoutingSnapshot routingSnapshot(ResolvedRouting routing) {
        if (routing == null) {
            return null;
        }
        String appId = null;
        String department = null;
        if (routing.routingInfo() != null) {
            appId = routing.routingInfo().app() == null ? null : routing.routingInfo().app().appId();
            department = routing.routingInfo().department() == null ? null : routing.routingInfo().department().name();
        }
        return new GatewayRoutingSnapshot(
                routing.ruleId(),
                routing.ruleCode(),
                routing.consumerId(),
                routing.returnUsageDetail(),
                routing.candidateModels() == null ? 0 : routing.candidateModels().size(),
                appId,
                department);
    }
}
