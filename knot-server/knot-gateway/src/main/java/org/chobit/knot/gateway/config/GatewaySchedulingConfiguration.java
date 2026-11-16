package org.chobit.knot.gateway.config;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 网关定时任务调度的公共配置：开启 {@code @Scheduled}，并提供所有节拍共用的 {@code TaskScheduler}。
 *
 * <p>Bean 名取 {@code taskScheduler}：{@code @Scheduled} 按类型查找调度器，与 Boot 自动配置同名即可覆盖它。
 * 池只开两个线程——节拍方法只负责「发起这一轮」，真正的并发在各自的 Runner 内部按批展开，
 * 池开大只会让同一个节拍多跑几份。</p>
 *
 * <p>关闭时等待在途节拍收尾（最长 30 秒）：任务进度都落在库里，中断不丢数据，这一步只避免半截更新。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class GatewaySchedulingConfiguration {

    @Bean(name = "taskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("gateway-schedule-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(throwable -> {
            // 兜底：任何漏出的异常都不能让调度器静默停摆
            LoggerFactory.getLogger(GatewaySchedulingConfiguration.class)
                    .error("Gateway schedule error", throwable);
        });
        return scheduler;
    }
}
