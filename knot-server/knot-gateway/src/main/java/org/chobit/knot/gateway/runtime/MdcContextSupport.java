package org.chobit.knot.gateway.runtime;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 阻塞段的调度与 MDC 重绑：把会阻塞线程的工作挪到阻塞专用调度器上，并在任务线程上重新绑定追踪上下文的 MDC。
 * event loop 线程上产生的日志（如流式转发的告警）不带 traceId。
 */
public final class MdcContextSupport {

    private MdcContextSupport() {
    }

    /**
     * 未注入专用调度器时的兜底：Reactor 的 {@code boundedElastic}（任务不会回到提交线程，池满时排队）。
     */
    public static Scheduler defaultBlockingScheduler() {
        return Schedulers.boundedElastic();
    }

    /**
     * 把 {@code work} 的订阅挪到 {@code scheduler} 上执行，并在任务线程上绑定 MDC。
     * 追踪上下文从 Reactor Context（{@link GatewayTraceContext#CONTEXT_KEY}）读取；取不到（单测 / 非 web 入口）时退化为纯调度切换。
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
