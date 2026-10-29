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
 * 异步任务的<b>节拍器</b>：只决定「什么时候扫」和「同时扫几个」，不决定「扫到之后做什么」。
 *
 * <p>⭐ <b>本类在 2026-10-29 被削薄</b>：原来它同时承担编排（查询、判定、状态机、计费、转储），
 * 现在那些全在 {@link AsyncTaskRunner}。理由不是美观——是<b>归属</b>：
 * 「这笔任务该不该结算」是业务判断，留在网关进程里意味着管理端要做对账必须复制一份，
 * 或者 import 网关的类。留下来的两件事恰好都是<b>部署</b>问题：跑不跑、跑多密、跑几个。
 * 判据见规范 {@code 10-backend-java.md §3.1}。</p>
 *
 * <h3>节拍与节奏分离</h3>
 * <p>cron 只决定「多久扫一次表」（默认每分钟整点），单个任务多久查一次由行上的
 * {@code next_poll_at} 决定（来自档案的轮询参数 + 退避）。于是调轮询间隔不用重启，
 * 不同任务也能有不同节奏。</p>
 *
 * <h3>cron 不保证串行，必须自己守卫</h3>
 * <p>Spring 的 {@code @Scheduled} 对同一方法不做互斥，用 {@code fixedDelay} 时靠「跑完再计时」
 * 天然串行，换 cron 后该性质消失。故每个节拍方法各带一个 {@link AtomicBoolean} 守卫：
 * 上一轮没跑完就<b>跳过</b>本次触发，而不是排队——排队会让欠账越滚越多，
 * 跳过则下一拍照常扫，效果与 fixedDelay 等价。</p>
 *
 * <h3>阻塞查询一律提交到 boundedElastic</h3>
 * <p>{@code @Scheduled} 线程不是 event loop，但仍不允许在上游 HTTP 上就地阻塞——统一走调度器，
 * 池满时排队，与网关其它阻塞段同口径。并发度取 {@link AsyncTaskProperties#getConcurrency()}：
 * 「同时发几个查询」，不是「占多少线程」。</p>
 *
 * <p>⚠ {@link #tick()} 会 {@code blockLast} 等待本批完成（阻塞的是调度线程池的线程，
 * 不是 Netty event loop）。因为耗时不可控，cron 下一拍可能已经到点——此时由 {@link #ticking}
 * 直接丢掉该拍。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskPoller {

    private final AsyncTaskProperties properties;
    private final AsyncTaskRunner runner;

    /**
     * 阻塞段调度器（{@code gatewayBlockingScheduler}）；单测可为 {@code null}，
     * 退化为 Reactor 共享的 {@code boundedElastic}。
     */
    @Nullable
    private final Scheduler blockingScheduler;

    private String nodeId;

    /**
     * 节拍守卫：cron 会按墙钟触发，与上一轮是否跑完无关。
     * 拿不到就跳过这一拍（不排队），避免同一批任务被两拍同时扫。
     */
    private final AtomicBoolean ticking = new AtomicBoolean();

    /** 归档节拍守卫，同 {@link #ticking} */
    private final AtomicBoolean archiving = new AtomicBoolean();

    @PostConstruct
    public void init() {
        validateBudget();
        this.nodeId = StringUtils.isNotBlank(properties.getNodeId())
                ? properties.getNodeId().trim()
                : hostname();
    }

    /**
     * 启动期校验「处理时间预算」：查询超时必须严格小于占位有效期。
     *
     * <p>⭐ 这条校验的收益全在<b>误配的那一天</b>：不满足时占位会在查询返回前过期，
     * 另一个节点把同一行重新领走，于是同一次上游调用被两个节点各查一次、各记一条
     * {@code task_query_seq} 相同的中间事件（数仓按 {@code (task_code, task_phase, task_query_seq)}
     * 去重，会把两次真实调用压成一条），{@code attempts} 也被两边各加一次导致退避跳档。
     * 症状是「偶发少算上游调用数」，事后按日志几乎无法还原，只能靠启动即炸。</p>
     *
     * <p>放在 {@code @PostConstruct} 而不是构造器：配置绑定在构造器之后完成。
     * 也刻意<b>留在 app 侧</b>——它读的是部署配置，与 {@link AsyncTaskRunner} 的业务规则无关。</p>
     */
    private void validateBudget() {
        long queryTimeout = properties.getQueryTimeoutMs();
        long claimTtl = properties.getClaimTtlMs();
        if (queryTimeout <= 0 || claimTtl <= queryTimeout) {
            throw new IllegalStateException("knot.gateway.async-task 配置不合法: claim-ttl-ms=" + claimTtl
                    + " 必须大于 query-timeout-ms=" + queryTimeout + "，且 query-timeout-ms 必须为正数。"
                    + "否则占位会在查询返回前过期，别的节点会重领同一行（重复上游调用、事件口径失真）");
        }
    }

    /**
     * 一轮扫描：回收超时 → 抢占待办 → 并发处理。
     *
     * <p>⚠ 这里的默认值与 {@link AsyncTaskDefaults#TICK_CRON}（即 {@link AsyncTaskProperties#getTickCron()}）
     * 是<b>同一个事实的第二份</b>，且<b>合并不了</b>：Spring 的 {@code @Scheduled} 要吃
     * {@code ${key:默认值}} 占位符才支持 yml 覆盖，而占位符里只能是字面量，放不进常量。
     * 两份漂移的后果是「yml 里没配的时候跑的是哪一份」说不清，故由 {@code AsyncTaskPropertiesTest}
     * 比对两边，删掉任一份都编译得过、但测试会红。</p>
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
     * 归档清理：删除保留窗口之前的终态行。
     *
     * <p>与 {@link #tick()} 分开成两个节拍：轮询是「对外承诺」，清理是「后台家务」，
     * 家务没跑不影响任何一次调用；同频只会让一张持续被扫的表再多一类写压力。</p>
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
     * 一次抢占一个号：轮询与超时回收各用各的，互不覆盖。
     *
     * <p>带上 {@code nodeId} 前缀是为了排查「这行是谁抢走的」——只看 UUID 只能知道
     * 「不是自己」，看不出是哪台机器卡住了。</p>
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
