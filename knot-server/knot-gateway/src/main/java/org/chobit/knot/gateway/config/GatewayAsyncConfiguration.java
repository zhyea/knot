package org.chobit.knot.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    /**
     * 配置缓存刷新执行器：Caffeine {@code refreshAfterWrite} 异步重载的<b>专用</b>池。
     *
     * <p><b>为什么要显式给</b>：不指定执行器时 Caffeine 用 {@code ForkJoinPool.commonPool()}，
     * 而 {@code GatewayConfigCacheService} 的 loader 是 MyBatis（阻塞 JDBC）。后果有两条：
     * 一是与并行流等 commonPool 使用者抢线程；二是 commonPool 的并行度只有 CPU 核数，
     * 一次慢查询就能把同一窗口内所有 key 的刷新串行排队。</p>
     *
     * <p><b>为什么池满丢弃而不是回落调用线程</b>：刷新是尽力而为的后台行为，丢了旧值继续服务，
     * 最迟 {@code expireAfterWrite}（10 分钟）后由下一次 {@code get} 同步回源，不会出现
     * 「配置永不过期」。反之若用 {@code CallerRunsPolicy}，刷新会就地跑在提交线程上——
     * 那是 Netty event loop，正是本类开头那段话要避免的局面。</p>
     *
     * <p>2 线程且不做配置项：刷新是幂等的单表查询，与 key 数量无关；
     * 并发刷新的收益远小于「再多一个要运维理解的旋钮」的成本。</p>
     */
    @Bean(name = "gatewayConfigRefreshExecutor", destroyMethod = "shutdown")
    public ExecutorService gatewayConfigRefreshExecutor() {
        return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(512),
                Thread.ofPlatform().name("gateway-cfg-refresh-", 1).daemon(true).factory(),
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
