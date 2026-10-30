package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.model.vo.response.AsyncTaskAcceptedResponse;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayHttpExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.runtime.GatewayStreamingResponse;
import org.chobit.knot.gateway.runtime.MdcContextSupport;
import org.chobit.knot.gateway.runtime.ReactiveHttpExchange;
import org.chobit.knot.gateway.model.vo.request.GatewayModelRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.Map;
import java.util.function.Function;

/**
 * 网关控制器的公共出口：把「一次网关请求」交给模板，再把模板的 {@link Mono} 接成 HTTP 响应。
 *
 * <p>2026-10-20（WebFlux 批2）：容器换成 Netty，返回类型从 {@code DeferredResult<Object>}
 * 改为 {@code Mono<ResponseEntity<Object>>}。两处语义变化：</p>
 * <ol>
 *   <li><b>载体显式传参</b>：{@link ReactiveHttpExchange} 由控制器收到的
 *       {@link ServerWebExchange} 包出来，随链路传递。不再有
 *       {@code ServletHttpExchange.current()} 这种线程本地取法（Netty 上必然取不到）；</li>
 *   <li><b>阻塞段换调度器</b>：路由解析（MyBatis/JDBC）与上游调用（内部 block）都是阻塞的，
 *       全部落在 {@code gatewayBlockingScheduler} 上，event loop 线程只做 I/O 调度。
 *       具体见 {@link MdcContextSupport#onBlockingScheduler}。</li>
 * </ol>
 */
public abstract class GatewayControllerSupport {

    private final GatewayRequestHandler requestHandler;

    /**
     * 阻塞段调度器（{@code Schedulers.newBoundedElastic}）：任务永不回到提交线程执行。
     */
    @Autowired(required = false)
    @Qualifier("gatewayBlockingScheduler")
    private Scheduler blockingScheduler;

    /**
     * 上游客户端配置：取 {@code requestBudget} 作为单条请求的总预算（B2 全局 deadline）。
     *
     * <p>非 Spring 场景（WebTestClient 独立装配控制器）没有这个 bean，此时退化为
     * <b>不加预算约束</b>——预算只是兜底，不影响请求链路的正确性。</p>
     */
    @Autowired(required = false)
    private GatewayUpstreamClientProperties upstreamProperties;

    protected GatewayControllerSupport(GatewayRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    /**
     * Delegates a typed gateway API request to the shared request template.
     *
     * @param exchange 容器交换对象（由 WebFlux 作为控制器方法入参注入）
     */
    protected Mono<ResponseEntity<Object>> handleRequest(String authorization,
                                                         String rule,
                                                         String traceparent,
                                                         String stickyKey,
                                                         GatewayModelRequest request,
                                                         ModelApiProtocolEnum protocol,
                                                         ServerWebExchange exchange) {
        return dispatch(exchange, http -> requestHandler.handle(
                authorization, rule, traceparent, stickyKey, request, protocol, http));
    }

    /**
     * Delegates a multipart gateway API request to the shared request template.
     */
    protected Mono<ResponseEntity<Object>> handleMultipartRequest(String authorization,
                                                                  String rule,
                                                                  String traceparent,
                                                                  String stickyKey,
                                                                  Map<String, Object> request,
                                                                  ModelApiProtocolEnum protocol,
                                                                  ServerWebExchange exchange) {
        return dispatch(exchange, http -> requestHandler.handleMultipart(
                authorization, rule, traceparent, stickyKey, request, protocol, http));
    }

    /**
     * Delegates a raw JSON gateway API request to the shared request template.
     *
     * <p>与 {@link #handleRequest} 的差别：入参是客户端原始 JSON 的 {@code Map} 而不是
     * 类型化 VO，用于「网关不认识字段、只负责转发」的端点（见
     * {@code AbstractGatewayRequestTemplate#handleJson}）。</p>
     */
    protected Mono<ResponseEntity<Object>> handleJsonRequest(String authorization,
                                                             String rule,
                                                             String traceparent,
                                                             String stickyKey,
                                                             Map<String, Object> request,
                                                             ModelApiProtocolEnum protocol,
                                                             ServerWebExchange exchange) {
        return dispatch(exchange, http -> requestHandler.handleJson(
                authorization, rule, traceparent, stickyKey, request, protocol, http));
    }

    /**
     * 把模板的 {@link Mono} 接成 HTTP 响应。
     *
     * <p>{@code requestBudget} 只覆盖「响应就绪」这一段（超时抛 {@code TimeoutException} → 504）：
     * 流式响应一旦开始写就是长连接，把总预算套在流上会在第 600 秒把正常的长回答掐断。</p>
     *
     * <p>异常原样透出（不在这里包一层），使 {@link GatewayExceptionHandler} 的既有分支
     * （502/429/401/503/504…）继续按原异常类型命中。</p>
     */
    private Mono<ResponseEntity<Object>> dispatch(ServerWebExchange exchange,
                                                 Function<GatewayHttpExchange, Mono<Object>> task) {
        ReactiveHttpExchange http = new ReactiveHttpExchange(exchange);
        Mono<Object> work = Mono.defer(() -> task.apply(http));
        GatewayUpstreamClientProperties properties = upstreamProperties;
        if (properties != null && properties.getRequestBudget() != null) {
            work = work.timeout(properties.getRequestBudget());
        }
        return MdcContextSupport.onBlockingScheduler(work, blockingScheduler)
                .flatMap(result -> respond(exchange, result));
    }

    /**
     * 响应写出：流式由本方法直接 {@code writeWith}（响应体是上游的 {@code Flux<DataBuffer>}，
     * 不经过序列化层），非流式把对象交给框架序列化。
     *
     * <p>流式分支返回空 {@code Mono}：响应已经由 {@code writeWith} 提交并完成，
     * 框架不要再往里写任何东西。</p>
     */
    private static Mono<ResponseEntity<Object>> respond(ServerWebExchange exchange, Object result) {
        if (result instanceof GatewayStreamingResponse stream) {
            return exchange.getResponse()
                    .writeWith(stream.body())
                    .then(Mono.empty());
        }
        // 异步任务提交回执：202 —— 客户端据此进入轮询，而不是把这份回应当成结果。
        // 判据是「响应体是不是那个回执类型」：能产出它的场合只有一次被接受的提交。
        if (result instanceof AsyncTaskAcceptedResponse accepted) {
            return Mono.just(ResponseEntity.accepted().body(accepted));
        }
        return Mono.just(ResponseEntity.ok().body(result));
    }
}
