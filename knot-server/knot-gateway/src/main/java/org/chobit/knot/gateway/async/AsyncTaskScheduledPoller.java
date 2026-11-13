package org.chobit.knot.gateway.async;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步任务的节拍器：只决定「什么时候扫」和「同时扫几个」，不决定「扫到之后做什么」；编排全在 {@link AsyncTaskRunner}。
 * cron 只决定「多久扫一次表」（默认每分钟整点），单个任务多久查一次由行上的 {@code next_poll_at} 决定。
 * Spring 的 {@code @Scheduled} 不保证串行，每个节拍方法各带 {@link AtomicBoolean} 守卫，上一轮没跑完就跳过本次触发。
 * 阻塞查询一律提交到 {@code boundedElastic}（池满排队），并发度取 {@link AsyncTaskProperties#getConcurrency()}；{@link #tick()} 用 {@code blockLast} 等本批完成，下一拍到点由 {@link #ticking} 丢掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskScheduledPoller {

    private final AsyncTaskProperties properties;
    private final AsyncTaskRunner runner;

    /**
     * 阻塞段调度器（{@code gatewayBlockingScheduler}）；单测可为 {@code null}，
     * 退化为 Reactor 共享的 {@code boundedElastic}。
     */
    @Nullable
    private final Scheduler blockingScheduler;

    /**
     * 回调交付编排。与 {@link #runner} 平级但独立：轮询把任务推到终态并武装交付，
     * 交付在终态之后单独跑一拍。分两个节拍，是为了让「上游慢」与「客户端慢」互不拖累。
     */
    private final AsyncTaskDeliveryRunner deliveryRunner;

    private String nodeId;

    /**
     * 节拍守卫：cron 按墙钟触发，与上一轮是否跑完无关；拿不到就跳过这一拍（不排队）。
     */
    private final AtomicBoolean ticking = new AtomicBoolean();

    /** 归档节拍守卫，同 {@link #ticking} */
    private final AtomicBoolean archiving = new AtomicBoolean();

    /** 交付节拍守卫，同 {@link #ticking} */
    private final AtomicBoolean delivering = new AtomicBoolean();

    @PostConstruct
    public void init() {
        validateBudget();
        this.nodeId = StringUtils.isNotBlank(properties.getNodeId())
                ? properties.getNodeId().trim()
                : hostname();
    }

    /**
     * 启动期校验「处理时间预算」：查询超时必须严格小于占位有效期。
     * 放在 {@code @PostConstruct}（配置绑定在构造器之后完成），留在 app 侧读取部署配置。
     */
    private void validateBudget() {
        long queryTimeout = properties.getQueryTimeoutMs();
        long claimTtl = properties.getClaimTtlMs();
        if (queryTimeout <= 0 || claimTtl <= queryTimeout) {
            throw new IllegalStateException("knot.gateway.async-task 配置不合法: claim-ttl-ms=" + claimTtl
                    + " 必须大于 query-timeout-ms=" + queryTimeout + "，且 query-timeout-ms 必须为正数。"
                    + "否则占位会在查询返回前过期，别的节点会重领同一行（重复上游调用、事件口径失真）");
        }
        // 交付是一条独立预算链：回调超时必须严格小于交付占位，理由与上面同——
        // 占位先过期会让同一笔结果被两个节点各投一次，客户看到重复回调且本地无报错。
        long deliveryTimeout = properties.getDeliveryTimeoutMs();
        long deliveryClaimTtl = properties.getDeliveryClaimTtlMs();
        if (properties.isDeliveryEnabled()
                && (deliveryTimeout <= 0 || deliveryClaimTtl <= deliveryTimeout)) {
            throw new IllegalStateException("knot.gateway.async-task 配置不合法: delivery-claim-ttl-ms="
                    + deliveryClaimTtl + " 必须大于 delivery-timeout-ms=" + deliveryTimeout
                    + "，且 delivery-timeout-ms 必须为正数。否则同一笔结果会被重复回调给客户端");
        }
    }

    /**
     * 一轮扫描：回收超时 → 抢占待办 → 并发处理。
     * 这里的默认值与 {@link AsyncTaskDefaults#TICK_CRON}（即 {@link AsyncTaskProperties#getTickCron()}）是同一个事实的第二份，合并不了：{@code @Scheduled} 占位符 {@code ${key:默认值}} 里只能是字面量。
     */
    @Scheduled(cron = "${knot.gateway.async-task.tick-cron:0 * * * * *}")
    public void tick() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!ticking.compareAndSet(false, true)) {
            log.debug("Previous async poll tick is still running, skip this beat");
            return;
        }
        try {
            String claimId = newClaimId();
            runner.expireTimedOut(claimId);
            pollDue(claimId);
        } catch (RuntimeException e) {
            // 一轮扫描的整体失败不能让定时器停摆：下一拍照常继续
            log.error("Async task poll tick failed", e);
        } finally {
            ticking.set(false);
        }
    }

    /**
     * 归档清理：删除保留窗口之前的终态行，与 {@link #tick()} 分成两个节拍。
     */
    @Scheduled(cron = "${knot.gateway.async-task.cleanup-cron:0 0 * * * *}")
    public void archive() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!archiving.compareAndSet(false, true)) {
            log.debug("Previous async task archive is still running, skip this beat");
            return;
        }
        try {
            runner.cleanup();
        } catch (RuntimeException e) {
            log.error("Async task cleanup failed", e);
        } finally {
            archiving.set(false);
        }
    }

    /**
     * 回调交付节拍：抢占到期待投递任务 → 并发投递。
     * 与 {@link #tick()} / {@link #archive()} 各用各的守卫。
     * 投递进度（{@code delivery_attempts / next_delivery_at}）全在库里，本拍崩溃后下一拍会重新抢占续投。
     */
    @Scheduled(cron = "${knot.gateway.async-task.delivery-cron:0 * * * * *}")
    public void deliver() {
        if (!properties.isEnabled() || !properties.isDeliveryEnabled()) {
            return;
        }
        if (!delivering.compareAndSet(false, true)) {
            log.debug("Previous async delivery tick is still running, skip this beat");
            return;
        }
        try {
            String claimId = newClaimId();
            List<AsyncTaskDto> claimed = deliveryRunner.claimDue(claimId);
            if (claimed.isEmpty()) {
                return;
            }
            Scheduler scheduler = blockingScheduler == null ? Schedulers.boundedElastic() : blockingScheduler;
            Flux.fromIterable(claimed)
                    .flatMap(task -> Mono.fromCallable(() -> deliveryRunner.deliver(task, claimId))
                            .subscribeOn(scheduler)
                            .onErrorResume(error -> {
                                log.error("Async task delivery failed: taskCode={}", task.taskCode(), error);
                                return Mono.empty();
                            }), properties.getConcurrency())
                    .blockLast();
        } catch (RuntimeException e) {
            log.error("Async task delivery tick failed", e);
        } finally {
            delivering.set(false);
        }
    }

    private void pollDue(String claimId) {
        List<AsyncTaskDto> claimed = runner.claimDue(claimId);
        if (claimed.isEmpty()) {
            return;
        }
        Scheduler scheduler = blockingScheduler == null ? Schedulers.boundedElastic() : blockingScheduler;
        Flux.fromIterable(claimed)
                .flatMap(task -> Mono.fromCallable(() -> runner.process(task))
                        .subscribeOn(scheduler)
                        .onErrorResume(error -> {
                            log.error("Async task processing failed: taskCode={}", task.taskCode(), error);
                            return Mono.empty();
                        }), properties.getConcurrency())
                .blockLast();
    }

    /**
     * 一次抢占一个号：轮询与超时回收各用各的，互不覆盖；占位号带 {@code nodeId} 前缀。
     */
    private String newClaimId() {
        return nodeId + "-" + UUID.randomUUID();
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "node-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
