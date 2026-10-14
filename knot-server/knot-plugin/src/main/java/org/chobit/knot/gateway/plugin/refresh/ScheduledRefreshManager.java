package org.chobit.knot.gateway.plugin.refresh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 定时刷新托管器：扫描全部 {@link Refreshable} bean，各自按间隔调度 {@code refresh()}（robin 2026-10-14）。
 *
 * <p>单例共享一个<b>守护</b>线程池（核心线程数 = 刷新任务数，避免某次慢刷新阻塞其余任务）；
 * 容器销毁时 {@link PreDestroy} 统一 shutdown。各任务互相隔离：单个任务未捕获异常只告警，
 * 不影响兄弟任务与后续 tick。</p>
 *
 * <p>用 {@code scheduleWithFixedDelay} 而非 fixedRate：刷新动作若偶发超过间隔，不会堆叠积压。</p>
 */
@Component
public class ScheduledRefreshManager {

    private static final Logger log = LoggerFactory.getLogger(ScheduledRefreshManager.class);

    private final List<Refreshable> refreshables;
    private final int poolSize;
    private final ScheduledExecutorService scheduler;

    public ScheduledRefreshManager(List<Refreshable> refreshables) {
        this.refreshables = refreshables == null ? List.of() : refreshables;
        this.poolSize = Math.max(1, this.refreshables.size());
        this.scheduler = Executors.newScheduledThreadPool(poolSize, new ThreadFactory() {
            private int seq;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "scheduled-refresh-" + (++seq));
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * 按各资源声明的间隔启动调度；间隔非正的资源跳过。
     */
    @PostConstruct
    public void start() {
        int scheduled = 0;
        for (Refreshable r : refreshables) {
            Duration interval = r.refreshInterval();
            if (interval == null || interval.toMillis() <= 0) {
                log.info("Refreshable {} skipped: refreshInterval not positive", r.getClass().getSimpleName());
                continue;
            }
            long period = interval.toMillis();
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    r.refresh();
                } catch (Throwable t) {
                    log.warn("Scheduled refresh failed for {}: {}", r.getClass().getSimpleName(), t.getMessage());
                }
            }, period, period, TimeUnit.MILLISECONDS);
            scheduled++;
        }
        log.info("ScheduledRefreshManager started: {} task(s) scheduled, pool size = {}", scheduled, poolSize);
    }

    /**
     * 停止所有调度：容器销毁时由 {@link PreDestroy} 触发，避免守护线程泄漏。
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
