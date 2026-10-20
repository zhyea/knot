package org.chobit.knot.gateway.runtime;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 阻塞段的调度与 MDC 重绑：把一段「会阻塞线程」的工作挪到阻塞专用调度器上，
 * 并在任务线程上重新绑定追踪上下文的 MDC。
 *
 * <p><b>为什么必须换调度器（2026-10-20，WebFlux 批2）</b>：容器换成 Netty 后，请求处理跑在
 * event loop 线程上。路由解析走 MyBatis（JDBC 阻塞）、上游调用内部 {@code block()}，
 * 这些一旦落在 event loop 上就是<b>把整个 I/O 线程卡死</b>——一个慢 SQL 能拖垮同线程上的
 * 所有连接。旧实现用的 {@code ThreadPoolTaskExecutor} + {@code CallerRunsPolicy} 更糟：
 * 池满时任务会<b>回落到调用线程就地执行</b>，而那个调用线程现在正好是 event loop。</p>
 *
 * <p><b>为什么 MDC 要在这里重绑</b>：MDC 是线程本地变量，跨线程不会自动传播。Reactor 的
 * 正确做法是自动上下文传播（{@code Hooks.enableAutomaticContextPropagation}），但那需要额外的
 * ThreadLocal 注册机制，且对每个操作符都加一层捕获开销。网关只关心「阻塞段里的业务日志
 * 带不带 traceId」，因此在<b>两个换线程的落点</b>上显式重绑，代价最小、行为最可预期。</p>
 *
 * <p>⚠ 已知残留：event loop 上产生的日志（如流式转发的告警）不带 traceId——Netty 上
 * 那些线程被大量请求复用，本来就不能可靠地持有单请求的 MDC。</p>
 */
public final class MdcContextSupport {

    private MdcContextSupport() {
    }

    /**
     * 未注入专用调度器时的兜底：Reactor 的 {@code boundedElastic}。
     *
     * <p>它是「包裹阻塞调用」的专用调度器——任务<b>永远不会回到提交线程</b>执行，
     * 池满时排队而不是回落，这正是 Netty 容器下必须的语义。</p>
     */
    public static Scheduler defaultBlockingScheduler() {
        return Schedulers.boundedElastic();
    }

    /**
     * 把 {@code work} 的订阅挪到 {@code scheduler} 上执行，并在任务线程上绑定 MDC。
     *
     * <p>追踪上下文从 Reactor Context（{@link GatewayTraceContext#CONTEXT_KEY}）读取——
     * 由 {@code GatewayTraceFilter} 在入口写入，因此这里不需要再传参数。
     * 取不到（单测 / 非 web 入口）时退化为纯调度切换，不绑定 MDC。</p>
     *
     * @param work      要执行的链路（其内部同步段会在任务线程上就地执行）
     * @param scheduler 阻塞段调度器
     */
    public static <T> Mono<T> onBlockingScheduler(Mono<T> work, Scheduler scheduler) {
        Scheduler target = scheduler == null ? defaultBlockingScheduler() : scheduler;
        return Mono.deferContextual(view -> {
            GatewayTraceContext trace = view.getOrDefault(GatewayTraceContext.CONTEXT_KEY, null);
            if (trace == null) {
                return work.subscribeOn(target);
            }
            // defer 的 supplier 在 subscribeOn 之后被调用 → 这里已经在任务线程上
            return Mono.defer(() -> {
                        trace.bindMdc();
                        return work;
                    })
                    .subscribeOn(target)
                    .doFinally(signal -> trace.clearMdc());
        });
    }
}
