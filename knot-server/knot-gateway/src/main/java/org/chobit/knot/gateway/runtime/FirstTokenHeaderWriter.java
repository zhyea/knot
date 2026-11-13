package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.GatewayHeaders;

import java.util.function.LongConsumer;

/**
 * 首 token 响应时间（TTFT）写头器：把「请求进入网关模板处理 → 响应侧首个可观测产出」的耗时以响应头 {@code X-Knot-First-Token-Time}（毫秒）暴露给调用方。
 * 流式由 {@code ProxyStreamingFlux} 在首个数据到达、首次 write/flush 之前调用 {@link #mark()}；非流式由 {@code AbstractGatewayRequestTemplate} 在响应体就绪时调用。
 * {@link #mark()} 幂等：只有首次调用写头并通知观察者；响应承载缺失（{@link GatewayHttpExchange#noop()}）时跳过写头但观察者仍触发。
 * 观察者（{@link LongConsumer}）在 {@link #mark()} 内、写头之前以耗时（毫秒）回调一次，供摘除模块消费 TTFT（{@code GatewayRequestHandler#firstTokenObserver}）。
 */
public final class FirstTokenHeaderWriter {

    private final long receivedAtMillis;
    private final GatewayHttpExchange http;
    private final LongConsumer observer;
    private boolean marked;
    /**
     * 首次 {@link #mark()} 时算出的耗时（毫秒）；未 mark 为 {@code null}
     * ——数仓侧要区分「极快的首 token（0）」与「压根没有首 token（null）」。
     */
    private Long elapsedMillis;

    /**
     * @param receivedAtMillis 测量起点（{@link GatewayRequestContext#receivedAtMillis()}）
     * @param http             响应写入口，可为 {@code null}（降级为不写头）
     * @param observer         首 token 观察者，可为 {@code null}（无派生能力时不需要）
     */
    public FirstTokenHeaderWriter(long receivedAtMillis, GatewayHttpExchange http, LongConsumer observer) {
        this.receivedAtMillis = receivedAtMillis;
        this.http = http;
        this.observer = observer;
    }

    /**
     * 从请求上下文创建写头器。无观察者。
     */
    public static FirstTokenHeaderWriter of(GatewayRequestContext context, GatewayHttpExchange http) {
        return new FirstTokenHeaderWriter(context.receivedAtMillis(), http, null);
    }

    /**
     * 从请求上下文创建写头器并绑定首 token 观察者（耗时毫秒回调，只触发一次）。
     */
    public static FirstTokenHeaderWriter of(GatewayRequestContext context,
                                            GatewayHttpExchange http,
                                            LongConsumer observer) {
        return new FirstTokenHeaderWriter(context.receivedAtMillis(), http, observer);
    }

    /**
     * 无响应载体的降级实例：{@link #mark()} 静默跳过写头。
     */
    public static FirstTokenHeaderWriter noop() {
        return new FirstTokenHeaderWriter(0L, GatewayHttpExchange.noop(), null);
    }

    /**
     * 标记首个可观测产出已到达：先通知观察者，再写入响应头。幂等，必须在响应体第一次 write/flush 之前调用。
     */
    public void mark() {
        if (marked) {
            return;
        }
        marked = true;
        elapsedMillis = Math.max(0L, System.currentTimeMillis() - receivedAtMillis);
        if (observer != null) {
            observer.accept(elapsedMillis);
        }
        if (http != null) {
            http.setResponseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME, Long.toString(elapsedMillis));
        }
    }

    /**
     * 首 token 耗时（毫秒）；尚未标记过时返回 {@code null}。
     */
    public Long elapsedMillis() {
        return elapsedMillis;
    }
}
