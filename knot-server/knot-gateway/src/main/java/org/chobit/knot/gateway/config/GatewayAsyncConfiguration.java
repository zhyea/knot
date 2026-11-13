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
 * 网关阻塞段调度器。
 * 两处必须阻塞：路由解析走 MyBatis（JDBC），上游调用等 {@code WebClient} 结果时 {@code block()}；一律在 {@link Scheduler} 上执行，Netty event loop 只做 I/O 调度。
 * 用 {@code newBoundedElastic}：任务永不回到调用线程，池满时排队。
 * 线程数由 {@code knot.gateway.upstream.async-executor-threads} 决定（同时在途的阻塞段上限）；队列 16 倍削峰，排队上限为硬边界。
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
     * 配置缓存刷新执行器：Caffeine {@code refreshAfterWrite} 异步重载的专用池。
     * 不指定执行器时 Caffeine 用 {@code ForkJoinPool.commonPool()}，而 {@code GatewayConfigCacheService} 的 loader 是 MyBatis（阻塞 JDBC）。
     * 池满丢弃，不回落调用线程；最迟 {@code expireAfterWrite}（10 分钟）后由下一次 {@code get} 同步回源。
     * 2 线程且不做配置项。
     */
    @Bean(name = "gatewayConfigRefreshExecutor", destroyMethod = "shutdown")
    public ExecutorService gatewayConfigRefreshExecutor() {
        return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(512),
                Thread.ofPlatform().name("gateway-cfg-refresh-", 1).daemon(true).factory(),
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
