package org.chobit.knot.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * 网关阻塞段调度器（原「B1 异步化承载池」的 Reactor 版本）。
 *
 * <p>网关整条链路是响应式的，但有两处**必须阻塞**：路由解析走 MyBatis（JDBC），
 * 上游调用内部在等 {@code WebClient} 结果时 {@code block()}。这两处一律在
 * {@link Scheduler} 上执行，容器（Netty）的 event loop 线程始终只做 I/O 调度。</p>
 *
 * <p><b>为什么是 {@code newBoundedElastic} 而不是 {@code ThreadPoolTaskExecutor}</b>：
 * 旧实现用 {@code CallerRunsPolicy}——池满时任务回落到提交线程就地执行。切到 Netty 之后
 * 提交线程可能就是 event loop，一次阻塞就把 I/O 线程卡死。{@code boundedElastic}
 * 的语义恰好相反：<b>任务永不回到调用线程</b>，池满时排队。</p>
 *
 * <p>线程数仍由 {@code knot.gateway.upstream.async-executor-threads} 决定，
 * 语义不变——它就是「同时在途的阻塞段上限」。队列给到 16 倍作为削峰缓冲；
 * 排队上限是硬边界，宁可拒绝也不无限堆积（内存优先）。</p>
 */
@Configuration(proxyBeanMethods = false)
public class GatewayAsyncConfiguration {

    /**
     * 阻塞段调度器：{@code destroyMethod="dispose"} 保证容器关闭时回收线程。
     */
    @Bean(name = "gatewayBlockingScheduler", destroyMethod = "dispose")
    public Scheduler gatewayBlockingScheduler(GatewayUpstreamClientProperties properties) {
        int threads = properties.getAsyncExecutorThreads();
        return Schedulers.newBoundedElastic(threads, threads * 16, "gateway-async-", 60, true);
    }
}
