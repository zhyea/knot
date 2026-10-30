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
 * Handles the incoming request flow. Executes the public operation.
 *
 * <p>2026-10-20（WebFlux 批1-B）：链路返回值由 {@code CompletableFuture} 改为 {@link Mono}，
 * 且<b>不再依赖 {@code RequestContextHolder} 传递请求属性</b>——响应读写的载体
 * （{@link GatewayHttpExchange}）随 {@link GatewayExchange} 显式传参。原先每个异步回调都要
 * {@code withRequestContext} 重绑线程本地属性的写法已全部删除：换成 Reactor 线程模型后，
 * 那种隐式传递必然失效且<b>不报错</b>（响应头静默丢失）。</p>
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
     *
     * @param http 本次请求的 HTTP 读写入口（Servlet / 未来的 reactive 实现），
     *             由控制器在请求线程上取好后显式传入
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
     * Gateway request template entrypoint for raw JSON passthrough requests.
     *
     * <p>与 {@link #handle} 的差别只是入参形态：{@code handle} 收类型化
     * {@link GatewayModelRequest}（字段集由本项目定义），本方法收客户端原始 JSON 反序列化成的
     * {@code Map}——用于「网关不认识字段、只负责转发」的端点（如 {@code /images/edits}）；
     * 上游字段增减不需要同步改本项目的 VO，也不会因为漏绑某个字段而把它丢掉。</p>
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
     *
     * <p>2026-10-20（WebFlux 批2）：这是 ThreadLocal → Reactor Context 的落点。
     * 批1 时代靠 {@code GatewayTraceContext.currentOrCreate(...)} 读线程本地值，
     * 而异步线程上那个值永远是空的——filter 分配的 spanId 与实际使用的对不上。</p>
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
        GatewayRequestContext context = new GatewayRequestContext(
                authorization, ruleCode, traceContext, protocol, stickyKey);
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
     *
     * <p>合并后单条流量事件在终态发出（非流式即此处用量记账后；流式在响应体写完的
     * finally；异常在 {@link #onTerminalError}）。「真正请求的模型」= 候选循环落定的 hit target，
     * 与处置结果同一条消息携带，不再分两层、不再需乱序修正。</p>
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
     *
     * <p>路由未解析时 {@code routing} 为 null、{@code error.stage=header_validate}，
     * 一条记录即可表达，数仓无需 left join 识别。</p>
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
     *
     * <p>⭐ 本方法是<b>全类唯一的发布出口</b>：非流式成功（{@link #onProxyResult}）、
     * 终态异常（{@link #onTerminalError}）、流式收尾（子类 {@code streamResponse} 的 onComplete）
     * 三处都走这里，{@code final} 防的就是再长出第四个出口。</p>
     *
     * <p><b>本层不判断「该不该发」，只负责发</b>：一次请求在上游真实发生了几次调用就发几条，
     * 「哪条是终态、哪条是中间过程」由事件自带的 {@code task_*} 字段表达、由数仓聚合、
     * 语义留在数据里而不留在网关的分支里。⚠ 反例见 2026-10-28 的路 A/B 取舍：
     * 曾用子类钩子把异步提交事件整体丢掉，等于把「1 次提交 + N 次查询」压成「1 条终态」，
     * 上游成本与延迟分布全部算不出来（`docs/异步任务表与通用轮询方案-2026-10-27.md` §3.2）。</p>
     *
     * <p>事件内容：路由维度（入口 / 规则 / 应用 / 部门 / 用户）、真正请求的模型（hit target）、
     * 结果 / 用量 / 计费 / 错误 / 上游调用明细同一条消息携带。
     * 入口即拒（路由未解析）照样发，只是 {@code routing} / {@code hit} 为 null、
     * {@code error.stage=header_validate}。</p>
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
