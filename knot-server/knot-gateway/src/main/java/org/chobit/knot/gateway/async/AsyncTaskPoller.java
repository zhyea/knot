package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.gateway.archive.ArchiveAsset;
import org.chobit.knot.gateway.archive.ArchiveStore;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步任务轮询器：{@code @Scheduled} 节拍 + 行级 {@code next_poll_at} 节奏。
 *
 * <p><b>节拍与节奏分离</b>：cron 只决定「多久扫一次表」（默认每分钟整点，
 * 2026-10-28 由每秒改来），单个任务多久查一次由行上的 {@code next_poll_at} 决定
 * （来自档案的轮询参数 + 退避）。于是调轮询间隔不用重启，不同任务也能有不同节奏。</p>
 *
 * <p><b>cron 不保证串行，必须自己守卫</b>：Spring 的 {@code @Scheduled} 对同一方法不做互斥，
 * 用 {@code fixedDelay} 时靠「跑完再计时」天然串行，换 cron 后该性质消失。故每个节拍方法
 * 各带一个 {@link AtomicBoolean} 守卫：上一轮没跑完就<b>跳过</b>本次触发，而不是排队——
 * 排队会让欠账越滚越多，跳过则下一拍照常扫，效果与 fixedDelay 等价。</p>
 *
 * <p><b>多节点靠 DB 原子抢占</b>，不引分布式锁：待办集合就是 {@code kb_async_tasks} 本身，
 * 抢占就是一次 {@code UPDATE ... ORDER BY ... LIMIT}。节点宕机不释放占位，靠
 * {@code claim_expires_at} 自动回收，于是优雅停机不需要任何额外钩子。</p>
 *
 * <p>⭐ <b>占位是租约，不是锁</b>：拿到只是「这一轮由我来做」，<b>每一次写都要带着它</b>
 * （{@code scheduleNext} / {@code complete} 的 {@code claim_id} 条件）。少了这一条，
 * 租约过期后原节点仍能写库：它会把新主人刚立的占位抹成 null，第三个节点随即再领一次同一行，
 * 重复处理就这么滚起来。租约长度见 {@link AsyncTaskProperties#getClaimTtlMs()}，
 * 它必须覆盖「查询 + 转储 + 落终态」，启动期由 {@link #validateBudget()} 校验。
 * 并发最多到此为止——结果唯一性由 {@code complete} 的 CAS 兜底，租约只负责「不要把上游调用算两遍」。</p>
 *
 * <p><b>阻塞查询一律提交到 boundedElastic</b>：{@code @Scheduled} 线程不是 event loop，
 * 但仍不允许在上游 HTTP 上就地阻塞——统一走调度器，池满时排队，与网关其它阻塞段同口径。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskPoller {

    private final AsyncTaskProperties properties;
    private final AsyncTaskStore store;
    private final AsyncTaskSupport support;
    private final AsyncTaskQueryClient queryClient;
    private final AsyncTaskCompletion completion;
    /**
     * 阻塞段调度器（{@code gatewayBlockingScheduler}）；单测可为 {@code null}，
     * 退化为 Reactor 共享的 {@code boundedElastic}。
     */
    @Nullable
    private final Scheduler blockingScheduler;

    /**
     * 产物转储；单测可为 {@code null}，此时直接用供应商地址（与 {@code passthrough} 同效）。
     *
     * <p>⚠ 生产环境不会是 null：默认实现由 {@code PassthroughArchiveStore} 兜底。
     * 这里允许 null 只是为了不让单测被迫构造一个转储器。</p>
     */
    @Nullable
    private final ArchiveStore archiveStore;

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
     * <p>放在 {@code @PostConstruct} 而不是构造器：配置绑定在构造器之后完成。</p>
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
     * 一轮扫描：抢占 → 并发处理 → 顺带回收超时任务。
     *
     * <p>⚠ 这里会 {@code blockLast} 等待本批完成（阻塞的是调度线程池的线程，不是 Netty event loop）。
     * 因为耗时不可控，cron 下一拍可能已经到点——此时由 {@link #ticking} 直接把该拍丢掉。</p>
     *
     * <p>⚠ 这里的默认值与 {@link AsyncTaskDefaults#TICK_CRON}（即 {@link AsyncTaskProperties#getTickCron()}）是
     * <b>同一个事实的第二份</b>，且<b>合并不了</b>：Spring 的 {@code @Scheduled} 要吃 {@code ${key:默认值}}
     * 占位符才支持 yml 覆盖，而占位符里只能是字面量，放不进常量。两份漂移的后果是
     * 「yml 里没配的时候跑的是哪一份」说不清，故由 {@code AsyncTaskPropertiesTest} 比对两边，
     * 删掉任一份都编译得过、但测试会红。</p>
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
            expireTimedOut();
            pollDue();
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
            int removed = store.cleanup(LocalDateTime.now().minusDays(Math.max(1, properties.getRetentionDays())),
                    properties.getCleanupBatchSize());
            if (removed > 0) {
                log.info("Async tasks archived: removed={} retentionDays={}", removed, properties.getRetentionDays());
            }
        } catch (RuntimeException e) {
            log.error("Async task cleanup failed", e);
        } finally {
            archiving.set(false);
        }
    }

    /**
     * 回收超时任务：先抢占，再逐条走状态机（{@link AsyncTaskEvent#TIMEOUT}）。
     *
     * <p>⭐ 不能再用一条批量 {@code UPDATE} 收掉：那会绕过状态机，也绕过 {@code finish} 里的
     * 终态事件发布，结果是这笔任务在数仓里<b>永远没有 {@code task_terminal=true} 的那一条</b>——
     * 按终态口径算的计费与成功率都会漏掉它（提交与中间查询两条还在，反而更容易被误读成
     * 「任务卡住了」）。代价是每行一次 PK 更新而非一次批量更新，
     * 批量上限仍由 {@code expire-batch-size} 控制。</p>
     *
     * <p>⭐ 也不能「读到就改」：读到的那一刻别的节点可能正在处理这行。先 {@code claimExpired}
     * 拿下租约，才敢说这行的终态由我定——否则本节点会用一个过期结论覆盖别人即将得出的真实结论。</p>
     */
    private void expireTimedOut() {
        List<AsyncTaskEntity> expired = store.claimExpired(newClaimId(),
                Duration.ofMillis(properties.getClaimTtlMs()), properties.getExpireBatchSize());
        if (expired.isEmpty()) {
            return;
        }
        log.warn("Async tasks expired before terminal state: count={}", expired.size());
        for (AsyncTaskEntity task : expired) {
            AsyncTaskContext context = JsonKit.fromJson(task.getContextJson(), AsyncTaskContext.class);
            // 用行上已发生的查询次数，不是 0：一个查了 50 次才等到超时的任务，
            // 终态事件里写「查询 0 次」会让延迟分布凭空少一截
            int querySeq = Math.max(0, task.getAttempts() == null ? 0 : task.getAttempts());
            apply(new Polling(task, context, null, querySeq, null, null), AsyncTaskEvent.TIMEOUT);
        }
    }

    private void pollDue() {
        String claimId = newClaimId();
        List<AsyncTaskEntity> claimed = store.claim(claimId,
                Duration.ofMillis(properties.getClaimTtlMs()), properties.getBatchSize());
        if (claimed.isEmpty()) {
            return;
        }
        Scheduler scheduler = blockingScheduler == null ? Schedulers.boundedElastic() : blockingScheduler;
        Flux.fromIterable(claimed)
                .flatMap(task -> Mono.fromCallable(() -> process(task))
                        .subscribeOn(scheduler)
                        .onErrorResume(error -> {
                            log.error("Async task processing failed: taskCode={}", task.getTaskCode(), error);
                            return Mono.empty();
                        }), properties.getConcurrency())
                .blockLast();
    }

    /**
     * 处理一个任务：取数 → 判定事件 → 交给状态机 → 按动作执行副作用。
     *
     * <p>⭐ 这里只做两件事：<b>把事实取出来</b>（快照、次数、上游响应），
     * 以及<b>把事实翻译成一个事件</b>。「这个事件该落哪个状态、要不要计费」一律问
     * {@link AsyncTaskStateMachine}，不再在这里堆 if——否则加一种失败原因就要同时改判断、改错误码、
     * 改话术三处。</p>
     *
     * @return 是否落定了终态（供日志与未来统计使用）
     */
    boolean process(AsyncTaskEntity task) {
        AsyncTaskSpec spec = JsonKit.fromJson(task.getPollSpecJson(), AsyncTaskSpec.class);
        AsyncTaskContext context = JsonKit.fromJson(task.getContextJson(), AsyncTaskContext.class);
        if (spec == null || context == null) {
            log.error("Async task snapshot is unreadable, mark failed: taskCode={}", task.getTaskCode());
            return apply(new Polling(task, null, null, 0, null, null), AsyncTaskEvent.SNAPSHOT_INVALID);
        }
        int attempts = Math.max(0, task.getAttempts() == null ? 0 : task.getAttempts()) + 1;
        if (attempts > spec.maxAttempts()) {
            // 没有发出查询：querySeq 取行上已有的查询次数（本次只是一次「判定放弃」）
            return apply(new Polling(task, context, spec, attempts - 1, null, null),
                    AsyncTaskEvent.ATTEMPTS_EXHAUSTED);
        }
        String body;
        try {
            body = queryClient.query(context, spec, task.getUpstreamTaskId(), context.traceparent());
        } catch (RuntimeException e) {
            // 瞬时失败（网络 / 5xx / 限流）：退避后重试，不判终态
            log.warn("Async task query failed, will retry: taskCode={} attempts={}", task.getTaskCode(), attempts, e);
            return apply(new Polling(task, context, spec, attempts, null, null), AsyncTaskEvent.QUERY_ERROR);
        }
        Map<String, Object> queryBody = JsonKit.fromJson(body, new TypeReference<Map<String, Object>>() {
        });
        return apply(new Polling(task, context, spec, attempts, body, queryBody), pollEventOf(queryBody, spec));
    }

    /**
     * 上游查询结果 → 事件（<b>唯一的语义判定点</b>）。
     *
     * <p>只回答「上游说了什么」，不决定「落哪个状态」。三值必须穷尽，其中
     * 「上游说成功但取不到产物」是独立事件（{@link AsyncTaskEvent#RESULT_MISSING}）——
     * 并进成功侧会算错钱，并进失败侧会丢掉"上游行为异常"这个信号。</p>
     */
    private AsyncTaskEvent pollEventOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        return switch (support.terminalOf(queryBody, spec)) {
            case RUNNING -> AsyncTaskEvent.POLL_RUNNING;
            case FAILED -> AsyncTaskEvent.POLL_FAILED;
            case SUCCEEDED -> StringUtils.isBlank(support.resultUrlOf(queryBody, spec))
                    ? AsyncTaskEvent.RESULT_MISSING
                    : AsyncTaskEvent.POLL_SUCCEEDED;
        };
    }

    /**
     * 状态机结果的<b>唯一执行口</b>：写库、计费、发事件都从这里出去。
     *
     * <p>严格按 {@link AsyncTaskTransition.Action} 分派，不做二次判断——一旦在这里补
     * {@code if (event == ...)}，状态机就又退化成装饰。</p>
     */
    private boolean apply(Polling polling, AsyncTaskEvent event) {
        AsyncTaskEntity task = polling.task();
        AsyncTaskTransition transition = AsyncTaskStateMachine.transition(
                AsyncTaskStatusEnum.fromCode(task.getStatus()), event);
        return switch (transition.action()) {
            case REJECT -> {
                // 不补发查询事件：这行已由别的节点发过终态事件，我们这次查询属重复劳动，
                // 记了反而让「上游调用数」虚高。宁可少记一次，也不让同一 task_code 出现两条终态。
                log.warn("Async task transition rejected, no state change: taskCode={} from={} event={}",
                        task.getTaskCode(), transition.from(), event);
                yield true;
            }
            case RESCHEDULE -> {
                long delay = support.nextDelayMs(polling.attempts(), polling.spec());
                boolean advanced = store.scheduleNext(task.getId(), task.getClaimId(),
                        LocalDateTime.now().plusNanos(delay * 1_000_000L));
                if (advanced) {
                    // 这次查询确实打到了上游：发一条非终态事件，它是「1 次提交 + N 次查询」里的一个 N
                    publishQuery(polling, event);
                } else {
                    // 租约已易主（本节点这一轮处理超时，行被别的节点回收）：不报这一条。
                    // 抢到行的那一方会用同一个 query_seq 再查一次并上报，两个都报反而让
                    // 数仓按 (task_code, task_phase, task_query_seq) 去重时无从判断哪条是真的。
                    // 代价是上游调用数少记一次，只有 WARN 里的 taskCode 能事后补账。
                    log.warn("Async task claim lost, query event skipped: taskCode={} seq={} claimId={}",
                            task.getTaskCode(), polling.attempts(), task.getClaimId());
                }
                yield false;
            }
            case SETTLE_BILLED -> {
                String sourceUrl = support.resultUrlOf(polling.queryBody(), polling.spec());
                UsageAccounting usage = completion.account(task, polling.context(), polling.rawBody());
                // 先算钱再搬文件：搬是旁路，算钱是主账。两者互不依赖，但顺序反了会出现
                // 「文件已搬、计费抛异常、下次重查又搬一遍」——虽然按 taskCode 幂等不该产生副本，
                // 也没必要给实现方出这种题
                finish(task, polling.context(), transition.to(), polling.attempts(),
                        archive(task, polling.context(), sourceUrl), null, null, usage);
                yield true;
            }
            case SETTLE -> {
                finish(task, polling.context(), transition.to(), polling.attempts(), null,
                        event.errorCode(), messageOf(polling, event), null);
                yield true;
            }
        };
    }

    /**
     * 发布一次中间查询事件（非终态）。
     *
     * <p>只在 {@code RESCHEDULE} 分支调用，理由不是省事而是事实：{@code REJECT} 那次查询属于
     * 跟别的节点抢同一个任务的重复劳动，记进去会让上游调用数虚高；落终态的分支由
     * {@code finish} 自己发终态事件。</p>
     */
    private void publishQuery(Polling polling, AsyncTaskEvent event) {
        if (polling.context() == null) {
            return;
        }
        try {
            completion.publishQuery(polling.task(), polling.context(), polling.attempts(), event.errorCode());
        } catch (RuntimeException e) {
            // 观测旁路失败不能影响任务推进：这条丢了还有下一次查询
            log.error("Async task query event publish failed: taskCode={} seq={}",
                    polling.task().getTaskCode(), polling.attempts(), e);
        }
    }

    /**
     * 把产物搬到可控位置，返回客户端最终拿到的地址；失败一律回退供应商原地址。
     *
     * <p>⭐ <b>转储不是交付前置条件</b>。它挂在「上游说成功、产物 URL 已取到」之后，
     * 此时任务必然落 {@code SUCCEEDED}、钱必然要算。为一个可以事后补搬的旁路去否定整个终态，
     * 等于把「地址将来会过期」升级成「这次调用彻底失败」，亏得更多。</p>
     *
     * <p>三种回退情形都记 ERROR 且不向上抛：未配转储器（仅单测出现）、转储返回空白、转储抛异常。
     * 事后补搬的依据就是日志里的 {@code taskCode} 与 {@code sourceUrl}。</p>
     *
     * <p>只从 {@code SETTLE_BILLED} 调用，那条路径的 {@code context} 必非 null
     * （{@code process} 在快照不可读时就提前返回了），故此处不做空判——真出现也会被下面的 catch 兜住。</p>
     */
    private String archive(AsyncTaskEntity task, AsyncTaskContext context, String sourceUrl) {
        if (archiveStore == null || StringUtils.isBlank(sourceUrl)) {
            return sourceUrl;
        }
        try {
            String archived = archiveStore.archive(new ArchiveAsset(
                    task.getTaskCode(),
                    task.getUpstreamTaskId(),
                    sourceUrl,
                    context.modelCode(),
                    context.upstreamModelCode(),
                    context.providerAccountCode(),
                    context.ruleCode(),
                    LocalDateTime.now()));
            if (StringUtils.isBlank(archived)) {
                log.error("Video archive returned blank url, fall back to upstream: taskCode={} sourceUrl={}",
                        task.getTaskCode(), sourceUrl);
                return sourceUrl;
            }
            if (!StringUtils.equals(archived, sourceUrl)) {
                log.info("Video archived: taskCode={} url={}", task.getTaskCode(), archived);
            }
            return archived;
        } catch (RuntimeException e) {
            log.error("Video archive failed, fall back to upstream url: taskCode={} sourceUrl={}",
                    task.getTaskCode(), sourceUrl, e);
            return sourceUrl;
        }
    }

    /**
     * 错误话术：上游给了具体原因就用它，否则用事件自带的兜底话术。
     *
     * <p>只管「怎么说」，不管「去哪儿」——那是状态机的事。</p>
     */
    private String messageOf(Polling polling, AsyncTaskEvent event) {
        return switch (event) {
            case POLL_FAILED -> StringUtils.defaultIfBlank(
                    upstreamMessage(polling.queryBody()), event.defaultMessage());
            case ATTEMPTS_EXHAUSTED -> event.defaultMessage() + ": " + polling.spec().maxAttempts();
            default -> event.defaultMessage();
        };
    }

    /**
     * 一次轮询采集到的事实。攒成一个 record 而不是六个参数一路传：
     * 出事时只看这一处就知道「状态机执行时到底掌握了什么」。
     *
     * @param attempts 本次查询是第几次（写进事件的 {@code task_query_seq}，也用于退避计算）。
     *                 ⚠ 没有真的发出查询的事件（快照不可读、次数用尽、knot 侧超时）传的是
     *                 <b>行上已有的查询次数</b>，不是 +1——它们不代表又查了一次上游
     */
    private record Polling(AsyncTaskEntity task,
                           AsyncTaskContext context,
                           AsyncTaskSpec spec,
                           int attempts,
                           String rawBody,
                           Map<String, Object> queryBody) {
    }

    /**
     * 落终态并发布事件：CAS 成功才发，「一条任务一条终态消息」由此保证。
     *
     * <p>CAS 的条件是「在途态 + 仍持有占位」两个：前者挡「别人已经落了终态」，
     * 后者挡「我这一轮处理超时、行已被回收」。两者都表现为 {@code complete} 返回 false，
     * 处理方式相同——写不进的行就不再由我报数。</p>
     *
     * @param querySeq 本次任务一共查了几次上游；写进终态事件的 {@code task_query_seq}，
     *                 它是「产物要等多久」的唯一量化口径（与提交事件配对即得延迟分布）
     */
    private void finish(AsyncTaskEntity task,
                        AsyncTaskContext context,
                        AsyncTaskStatusEnum status,
                        int querySeq,
                        String resultUrl,
                        String errorCode,
                        String errorMessage,
                        UsageAccounting usage) {
        boolean settled = store.complete(task.getId(), task.getClaimId(), status.code(),
                resultJson(resultUrl), errorCode, errorMessage);
        if (!settled) {
            log.warn("Async task not settled by this node (already settled or claim lost), "
                            + "terminal event skipped: taskCode={} status={} claimId={}",
                    task.getTaskCode(), status, task.getClaimId());
            return;
        }
        if (context == null) {
            return;
        }
        Throwable error = errorCode == null ? null : new GatewayUpstreamException(
                StringUtils.defaultString(errorMessage, errorCode), errorCode);
        try {
            completion.publishTerminal(task, context, querySeq, usage, error);
        } catch (RuntimeException e) {
            // 事件发布失败不影响任务终态：钱已经算过了，事件是旁路观测
            log.error("Async task terminal event publish failed: taskCode={}", task.getTaskCode(), e);
        }
    }

    private String resultJson(String resultUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_url", resultUrl);
        return JsonKit.toJson(result);
    }

    private String upstreamMessage(Map<String, Object> queryBody) {
        Object message = queryBody == null ? null : queryBody.get("message");
        return message == null ? null : StringUtils.abbreviate(String.valueOf(message), 1000);
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
