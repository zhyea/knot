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
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

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
     * 流量事件主题全名（埋点业务侧维护，取自 {@code kafka.topic.traffic}）。
     *
     * <p>2026-10-18 合并：原请求层 / 响应层两个主题收敛为一个——数仓离线批处理，
     * 不需要早期「实时流量入口」信号，单主题单消息在终态一次发出即可。</p>
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
     * Gateway request template entrypoint:
     * validate required headers,
     * resolve routing,
     * proxy the adapted request upstream, then apply post-response usage accounting.
     */
    public final CompletableFuture<Object> handle(String authorization,
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
    public final CompletableFuture<Object> handleMultipart(String authorization,
                                        String ruleCode,
                                        String traceparent,
                                        String stickyKey,
                                        Map<String, Object> requestBody,
                                        ModelApiProtocolEnum protocol) {
        return handleMap(authorization, ruleCode, traceparent, stickyKey,
                requestBody, protocol, MediaType.MULTIPART_FORM_DATA);
    }

    private CompletableFuture<Object> handleMap(String authorization,
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
        // 请求上下文属性（RequestContextHolder）随异步链路传递：在首个异步回调里重新绑定，
        // 保证流式响应头 / 首 token 响应头等在正确的请求线程上写出。
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        try {
            context.apiKey(validateRequestHeaders(authorization, ruleCode));
            ResolvedRouting routingInfo = resolveRouting(context);
            context.routing(routingInfo);
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_REQUEST, null, null);

            // proxy 链（重试 + failover）全程异步，退避期间不阻塞任何线程
            CompletableFuture<ProxyResult> proxyFuture = proxy(context, exchange, requestAttributes);
            return proxyFuture.thenApply(result -> withRequestContext(requestAttributes, () -> {
                exchange.proxyResult(result);
                // 合并后单条流量事件在终态发出（非流式即此处用量记账后；流式在响应体写完的
                // finally；异常在 catch）。「真正请求的模型」= 候选循环落定的 hit target，
                // 与处置结果同一条消息携带，不再分两层、不再需乱序修正。
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
                    publishTraffic(context, exchange, proxied == null ? null : proxied.usage(),
                            firstTokenWriter.elapsedMillis(), null);
                }
                return response;
            })).exceptionally(ex -> {
                // 任意终态（含入口即拒、上游失败、计费异常）都发一条流量事件：路由未解析时 routing 为 null、
                // error.stage=header_validate，一条记录即可表达，数仓无需 left join 识别。
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
                RequestAttributes previous = RequestContextHolder.getRequestAttributes();
                RequestContextHolder.setRequestAttributes(requestAttributes);
                try {
                    dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_ERROR, null, cause);
                    publishTraffic(context, exchange, null, null, cause);
                } finally {
                    RequestContextHolder.setRequestAttributes(previous);
                }
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause);
            });
        } catch (RuntimeException ex) {
            // 同步阶段（头校验 / 路由解析 / 插件）异常：路由未解析时 routing 为 null、
            // error.stage=header_validate，一条记录即可表达，数仓无需 left join 识别。
            dispatchGatewayPlugin(context, exchange, PluginStageCode.GATEWAY_ERROR, null, ex);
            publishTraffic(context, exchange, null, null, ex);
            throw ex;
        }
    }

    /**
     * 在指定请求上下文属性下执行任务，结束后还原调用线程原有的属性（可能为 null）。
     *
     * <p>异步重试 / failover 链会在不同线程上回调，而流式响应头、首 token 响应头、
     * 用量测试头解析等都依赖 {@link RequestContextHolder} 的线程本地属性，
     * 故每个异步回调都需先重新绑定请求属性再执行。</p>
     */
    protected static <T> T withRequestContext(RequestAttributes attributes, Supplier<T> task) {
        RequestAttributes previous = RequestContextHolder.getRequestAttributes();
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            return task.get();
        } finally {
            RequestContextHolder.setRequestAttributes(previous);
        }
    }

    /**
     * 发布流量事件：数仓输出的旁路开关关闭时整体跳过。
     *
     * <p>请求终态一次性发出——路由维度（入口 / 规则 / 应用 / 部门 / 用户）、真正请求的模型
     * （hit target）、结果 / 用量 / 计费 / 错误 / 上游调用明细同一条消息携带。
     * 入口即拒（路由未解析）照样发，只是 {@code routing} / {@code hit} 为 null、
     * {@code error.stage=header_validate}。</p>
     */
    private void publishTraffic(GatewayRequestContext context,
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

    protected abstract CompletableFuture<ProxyResult> proxy(GatewayRequestContext context, GatewayExchange exchange, RequestAttributes requestAttributes);

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
