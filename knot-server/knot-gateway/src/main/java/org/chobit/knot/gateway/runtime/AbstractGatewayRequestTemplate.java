package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.AuthConstants;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.service.support.ModerationWordResource;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.plugin.PluginDispatcher;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.gateway.GatewayPluginContext;
import org.chobit.knot.plugin.gateway.GatewayRoutingSnapshot;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.model.vo.request.GatewayModelRequest;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.LongConsumer;

/**
 * HTTP 请求处理模板基类：链路返回值统一为 {@link Mono}，HTTP 读写载体 {@link GatewayHttpExchange} 随 {@link GatewayExchange} 显式传参，
 * 链路中没有任何 ThreadLocal 依赖。
 */
public abstract class AbstractGatewayRequestTemplate {

    private final PluginDispatcher pluginDispatcher;

    /**
     * 词库资源供给：每请求取全量启用词条随插件上下文下发。
     * 列表由 {@link ModerationWordResource} 按路由数据同款节奏缓存（约 30s 生效延迟）；编译 / 去重 / 定时重建在 knot-plugin 的 {@code ModerationWordCache}。
     */
    private final ModerationWordResource moderationWordResource;

    /**
     * Kafka 投递器；未开启 Kafka 事件输出时为 {@code null}（{@code kafka.enabled=false}），埋点一律判空。
     * 主题常量由本类维护，publish 时显式传入。
     */
    private final KafkaPublisher eventPublisher;

    /**
     * 流量事件主题全名（埋点业务侧维护，取自 {@code kafka.topic.traffic}）。
     */
    private final String trafficTopic;

    protected AbstractGatewayRequestTemplate(PluginDispatcher pluginDispatcher,
                                             ModerationWordResource moderationWordResource,
                                             KafkaPublisher eventPublisher,
                                             String trafficTopic) {
        this.pluginDispatcher = pluginDispatcher;
        this.moderationWordResource = moderationWordResource;
        this.eventPublisher = eventPublisher;
        this.trafficTopic = trafficTopic;
    }

    /**
     * Gateway request template entrypoint: validate required headers, resolve routing,
     * proxy the adapted request upstream, then apply post-response usage accounting.
     *
     * @param http 本次请求的 HTTP 读写入口，由控制器在请求线程上取好后显式传入
     */
    public final Mono<Object> handle(String authorization,
                                     String ruleCode,
                                     String traceparent,
                                     String stickyKey,
                                     GatewayModelRequest request,
                                     ModelApiProtocolEnum protocol,
                                     GatewayHttpExchange http) {
        return Mono.deferContextual(view -> Mono.defer(() -> handleMap(authorization, ruleCode,
            traceOf(view, traceparent), stickyKey,
            JsonKit.toMap(request), protocol, MediaType.APPLICATION_JSON, http)));
    }

    /**
     * Gateway request template entrypoint for multipart requests.
     */
    public final Mono<Object> handleMultipart(String authorization,
                                              String ruleCode,
                                              String traceparent,
                                              String stickyKey,
                                              Map<String, Object> requestBody,
                                              ModelApiProtocolEnum protocol,
                                              GatewayHttpExchange http) {
        return Mono.deferContextual(view -> Mono.defer(() -> handleMap(authorization, ruleCode,
            traceOf(view, traceparent), stickyKey,
            requestBody, protocol, MediaType.MULTIPART_FORM_DATA, http)));
    }

    /**
     * Gateway request template entrypoint for raw JSON passthrough requests: the payload is the client's
     * raw JSON deserialized into a {@code Map}, used by endpoints that forward fields the gateway does not know
     * (e.g. {@code /images/edits}).
     */
    public final Mono<Object> handleJson(String authorization,
                                         String ruleCode,
                                         String traceparent,
                                         String stickyKey,
                                         Map<String, Object> requestBody,
                                         ModelApiProtocolEnum protocol,
                                         GatewayHttpExchange http) {
        return Mono.deferContextual(view -> Mono.defer(() -> handleMap(authorization, ruleCode,
            traceOf(view, traceparent), stickyKey,
            requestBody, protocol, MediaType.APPLICATION_JSON, http)));
    }

    /**
     * 追踪上下文：优先取 Reactor Context 里 {@code GatewayTraceFilter} 写好的那一份，
     * 取不到（非 web 入口 / 单测）才按 traceparent 自建。
     */
    private static GatewayTraceContext traceOf(ContextView view, String traceparent) {
        GatewayTraceContext fromContext = view.getOrDefault(GatewayTraceContext.CONTEXT_KEY, null);
        return fromContext == null ? GatewayTraceContext.fromTraceparent(traceparent) : fromContext;
    }

    private Mono<Object> handleMap(String authorization,
                                   String ruleCode,
                                   GatewayTraceContext traceContext,
                                   String stickyKey,
                                   Map<String, Object> requestBody,
                                   ModelApiProtocolEnum protocol,
                                   MediaType contentType,
                                   GatewayHttpExchange http) {
        // 上下文与载体先建出来，好让「入口即拒」也有一条响应层事件
        // （头校验失败时不发，数仓就统计不到 401/400 的拒绝量）
        GatewayRequestContext context = new GatewayRequestContext(authorization, ruleCode, traceContext, protocol, stickyKey);
        GatewayExchange exchange = new GatewayExchange(requestBody, contentType, http);
        try {
            context.apiKey(validateRequestHeaders(authorization, ruleCode));
            ResolvedRouting routingInfo = resolveRouting(context);
            context.routing(routingInfo);
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_REQUEST, null, null);

            // proxy 链（重试 + failover）全程异步，退避期间不阻塞任何线程
            return proxy(context, exchange)
                .map(result -> onProxyResult(context, exchange, result))
                .doOnError(cause -> onTerminalError(context, exchange, cause));
        } catch (RuntimeException ex) {
            // 同步阶段（头校验 / 路由解析 / 插件）异常：路由未解析时 routing 为 null、
            // error.stage=header_validate，一条记录即可表达，数仓无需 left join 识别。
            onTerminalError(context, exchange, ex);
            return Mono.error(ex);
        }
    }

    /**
     * 上游返回后的终态处理：用量记账 + 首 token 写头 + 响应层插件 + 流量事件。
     * 单条流量事件在终态发出：非流式在用量记账后，流式在响应体写完的 finally，异常在 {@link #onTerminalError}。
     * 「真正请求的模型」取候选循环落定的 hit target，与处置结果同一条消息携带。
     */
    private Object onProxyResult(GatewayRequestContext context, GatewayExchange exchange, ProxyResult result) {
        exchange.proxyResult(result);
        Object response = applyUsageAccounting(context, exchange);
        // 首 token 响应时间：非流式在此写头（响应体就绪即产出就绪，等价首 token）；
        // 流式返回 GatewayStreamingResponse，由响应体在上游首行写出前自行写头。
        // 观察者由子类按需提供（如慢信号摘除），两个出口共用同一钩子
        FirstTokenHeaderWriter firstTokenWriter = null;
        if (response != null && !(response instanceof GatewayStreamingResponse)) {
            firstTokenWriter = FirstTokenHeaderWriter.of(context, exchange.http(), firstTokenObserver(context));
            firstTokenWriter.mark();
        }
        dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_RESPONSE, response, null);
        // 流式不包括在此发布：响应体尚未写完，用量要等 ProxyStreamingFlux 收尾才知道，
        // 改由 GatewayRequestHandler 在流结束时发布（见 streamResponse）。
        if (firstTokenWriter != null) {
            ProxyResult proxied = exchange.proxyResult();
            // 非流式：响应体已就绪，用量在此就是终态
            publishTraffic(context, exchange, proxied == null ? null : proxied.usage(),
                firstTokenWriter.elapsedMillis(), null);
        }
        return response;
    }

    /**
     * 任意终态异常（含入口即拒、上游失败、计费异常）的唯一收口：插件错误阶段 + 流量事件。
     * 路由未解析时 {@code routing} 为 null、{@code error.stage=header_validate}，
     * 一条记录即可表达，数仓无需 left join 识别。
     */
    private void onTerminalError(GatewayRequestContext context, GatewayExchange exchange, Throwable thrown) {
        Throwable cause = unwrapCompletion(thrown);
        dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_ERROR, null, cause);
        publishTraffic(context, exchange, null, null, cause);
    }

    /**
     * 拆掉 {@link CompletionException} 这层壳：异常处理器按原始类型（502/429/401/503…）分支，
     * 包着一层就对不上。Reactor 链路本身不产生这层包装，但插件等既有代码可能抛。
     */
    static Throwable unwrapCompletion(Throwable thrown) {
        if (thrown instanceof CompletionException ex && ex.getCause() != null) {
            return ex.getCause();
        }
        return thrown;
    }

    /**
     * 发布流量事件：数仓输出的旁路开关关闭时整体跳过。
     * 本方法是全类唯一的发布出口（非流式成功、终态异常、流式收尾三处都走这里）；不判断「该不该发」，上游真实发生几次调用就发几条，
     * 哪条是终态由事件自带的 {@code task_*} 字段表达。
     * 事件内容：路由维度、hit target、结果 / 用量 / 计费 / 错误 / 上游调用明细同一条消息；入口即拒照样发，{@code routing} / {@code hit} 为 null、{@code error.stage=header_validate}。
     */
    protected final void publishTraffic(GatewayRequestContext context,
                                        GatewayExchange exchange,
                                        UsageAccounting usage,
                                        Long ttftMs,
                                        Throwable error) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publish(trafficTopic,
            GatewayTrafficMsgDto.of(context, exchange, usage, ttftMs, error));
    }


    protected abstract ResolvedRouting resolveRouting(GatewayRequestContext context);

    /**
     * 上游代理链（同候选重试 + 跨候选 failover）：整条链异步，退避期间不占用线程。
     */
    protected abstract Mono<ProxyResult> proxy(GatewayRequestContext context, GatewayExchange exchange);

    protected abstract Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange);

    /**
     * 首 token 观察者钩子：把首个可观测产出的耗时（毫秒）暴露给子类的派生能力（如异常摘除的慢信号计数）。
     * 默认无观察者；返回非 null 时 {@link FirstTokenHeaderWriter#mark()} 首次触发即以耗时回调一次（先于写头）。
     * 流式与非流式两个出口共用本钩子。
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
        if (routing.routingContext() != null) {
            appId = routing.routingContext().app() == null ? null : routing.routingContext().app().appId();
            department = routing.routingContext().department() == null ? null : routing.routingContext().department().name();
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
