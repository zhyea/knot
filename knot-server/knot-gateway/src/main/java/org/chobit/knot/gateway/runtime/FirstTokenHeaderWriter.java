package org.chobit.knot.gateway.runtime;

import jakarta.servlet.http.HttpServletResponse;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.LongConsumer;

/**
 * 首 token 响应时间（TTFT）写头器：把「请求进入网关模板处理 → 响应侧首个可观测产出」
 * 的耗时以响应头 {@code X-Knot-First-Token-Time}（毫秒）暴露给调用方。
 *
 * <p>两个出口共用同一实例与同一语义：</p>
 * <ul>
 *   <li>流式：由 {@code ProxyStreamingBody} 在上游首行数据到达、首次 write/flush 之前调用
 *       {@link #mark()}——Tomcat 在首次 flush body 时才 commit 响应头，此刻写头依然生效；</li>
 *   <li>非流式：由 {@code AbstractGatewayRequestTemplate} 在响应体就绪、返回控制器前调用。</li>
 * </ul>
 *
 * <p>{@link #mark()} 幂等：只有首次调用写头并通知观察者，后续调用（SSE 每行都会触发）直接短路。
 * response 缺失（非 web 场景、拿不到请求属性）时静默跳过写头，<b>但观察者仍会触发</b>——
 * 慢信号这类派生能力不依赖响应对象。</p>
 *
 * <p>观察者（{@link LongConsumer}）在 {@link #mark()} 内、写头之前以耗时（毫秒）回调一次，
 * 供摘除模块消费 TTFT（见 {@code GatewayRequestHandler#firstTokenObserver}）。</p>
 */
public final class FirstTokenHeaderWriter {

    private final long receivedAtMillis;
    private final HttpServletResponse response;
    private final LongConsumer observer;
    private boolean marked;
    /**
     * 首次 {@link #mark()} 时算出的耗时（毫秒）；未 mark 为 {@code null}
     * ——数仓侧要区分「极快的首 token（0）」与「压根没有首 token（null）」。
     */
    private Long elapsedMillis;

    /**
     * @param receivedAtMillis 测量起点（{@link GatewayRequestContext#receivedAtMillis()}）
     * @param response         当前请求的响应对象，可为 {@code null}（降级为不写头）
     * @param observer         首 token 观察者，可为 {@code null}（无派生能力时不需要）
     */
    public FirstTokenHeaderWriter(long receivedAtMillis, HttpServletResponse response, LongConsumer observer) {
        this.receivedAtMillis = receivedAtMillis;
        this.response = response;
        this.observer = observer;
    }

    /**
     * 从请求上下文创建写头器，response 取自当前请求属性；拿不到时降级为不写头。无观察者。
     */
    public static FirstTokenHeaderWriter of(GatewayRequestContext context) {
        return new FirstTokenHeaderWriter(context.receivedAtMillis(), currentResponse(), null);
    }

    /**
     * 从请求上下文创建写头器并绑定首 token 观察者（耗时毫秒回调，只触发一次）。
     */
    public static FirstTokenHeaderWriter of(GatewayRequestContext context, LongConsumer observer) {
        return new FirstTokenHeaderWriter(context.receivedAtMillis(), currentResponse(), observer);
    }

    /**
     * 无 response 的降级实例：{@link #mark()} 静默跳过写头。
     */
    public static FirstTokenHeaderWriter noop() {
        return new FirstTokenHeaderWriter(0L, null, null);
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
        if (response != null) {
            response.setHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME, Long.toString(elapsedMillis));
        }
    }

    /**
     * 首 token 耗时（毫秒）；尚未标记过时返回 {@code null}。
     */
    public Long elapsedMillis() {
        return elapsedMillis;
    }

    private static HttpServletResponse currentResponse() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        return servletAttributes.getResponse();
    }
}
