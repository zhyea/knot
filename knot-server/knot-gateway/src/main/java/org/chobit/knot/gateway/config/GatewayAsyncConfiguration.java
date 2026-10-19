package org.chobit.knot.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 网关异步请求处理线程池（B1 异步化的承载池）。
 *
 * <p>控制器返回 {@link org.springframework.web.context.request.async.DeferredResult} 后，真正的上游代理链路
 * ——含失败重试的退避等待——在该专用线程池上执行，Tomcat worker 线程立即释放，避免坏上游的
 * 重试/退避把 Web 容器线程吃干。线程数由 {@link GatewayUpstreamClientProperties#getAsyncExecutorThreads()}
 * 决定，即并发在途的上游调用上限。</p>
 *
 * <p>队列容量给到线程数的 4 倍作为背压缓冲；极端满载时 {@link ThreadPoolExecutor.CallerRunsPolicy}
 * 让提交线程（前一个异步任务或重投的延迟调度）就地执行，避免任务被静默丢弃导致请求无响应。</p>
 */
@Configuration(proxyBeanMethods = false)
public class GatewayAsyncConfiguration {

    @Bean(name = "gatewayAsyncExecutor")
    public ThreadPoolTaskExecutor gatewayAsyncExecutor(GatewayUpstreamClientProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = properties.getAsyncExecutorThreads();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(threads * 4);
        executor.setThreadNamePrefix("gateway-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }
}
