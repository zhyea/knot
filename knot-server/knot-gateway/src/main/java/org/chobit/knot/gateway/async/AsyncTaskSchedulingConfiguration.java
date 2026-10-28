package org.chobit.knot.gateway.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 异步任务轮询调度：启用 {@code @Scheduled} 并提供专用 {@code TaskScheduler}。
 *
 * <p><b>为什么必须自定义</b>：Spring 默认的任务调度器是<b>单线程</b>的，
 * 一个卡住的 tick 会连带停掉所有 {@code @Scheduled} 方法。这里给一个小线程池
 * （轮询的并发由 {@code AsyncTaskProperties#concurrency} 与 boundedElastic 承载，
 * 本池只负责「发起这一轮」，不需要大）。</p>
 *
 * <p>Bean 名取 {@code taskScheduler}：{@code @Scheduled} 按类型查找调度器，
 * 命名为默认名可覆盖 Boot 的自动配置，避免出现「两个调度器、行为随容器而变」。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AsyncTaskSchedulingConfiguration {

    @Bean(name = "taskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("async-task-");
        scheduler.setRemoveOnCancelPolicy(true);
        // 关闭时等待在途 tick：任务已落表，中断也不会丢，只是避免半截更新
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(throwable -> {
            // 兜底：任何漏出的异常都不能让调度器静默停摆
            org.slf4j.LoggerFactory.getLogger(AsyncTaskSchedulingConfiguration.class)
                    .error("Async task scheduler error", throwable);
        });
        return scheduler;
    }
}
