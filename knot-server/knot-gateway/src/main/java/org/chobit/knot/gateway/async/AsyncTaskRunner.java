package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.common.constants.enums.AsyncTaskDeliveryStatusEnum;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.archive.ArchiveAsset;
import org.chobit.knot.gateway.archive.ArchiveStore;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步任务的<b>编排</b>：抢占 → 查询 → 判定 → 走状态机 → 落终态/计费/发事件。
 *
 * <p>⭐ <b>为什么它在 {@code gateway}（2026-10-29 定稿，中间走过一次来回）</b>：第一版把它抽进了
 * {@code svc.async}（当时的包名，后来 Store 全撤、数据访问并入 {@code svc.service}），
 * 理由是「svc 只剩 Store 就是 DAO」。抽完量了一个事实——<b>{@code knot-admin}
 * 对这套能力的引用是 0</b>，消费方只有 gateway；而它依赖的每一样东西都是网关运行时
 * （WebClient 与渠道凭据、Kafka 流量事件、计费管线、traceparent 重放）。把只服务一个 app 的编排
 * 放进两 app 共用的 svc，结果是 svc 长出 gateway 专用语义，另一个 app 永远用不到。
 * 判据因此改成「<b>编排跟随使用方</b>」：svc 只留真正被两端共用的东西（这里是
 * {@code kb_async_tasks} 的读写与状态码），编排回它唯一的家。</p>
 *
 * <p>⚠ 它与改造前那个 496 行 {@code AsyncTaskPoller} 的差别不是「搬到哪」，而是<b>拆成了两层</b>：
 * poller 只管节拍（cron / 重叠拍跳过 / 并发扇出 / 预算校验），本类只管一条任务怎么推进。
 * 两者即便同模块，也不该写在同一个类里——搬迁只是副产品。</p>
 *
 * <p>⚠ <b>为什么不叫 {@code AsyncTaskService}</b>：svc 侧的数据访问类占用了这个名字
 * （{@code org.chobit.knot.svc.service.AsyncTaskService}），而本类要 import 它。同名会让 gateway
 * 里的引用永远指向自己，只能靠全限定名绕——那才是真的把读者搞晕。叫 {@code Runner} 也更准：
 * 它跑一轮、推进一行的位置，不持有任何表。</p>
 *
 * <h3>它不该做什么（边界比职责更重要）</h3>
 * <ul>
 *   <li><b>不做定时</b>：{@code @Scheduled}、cron 表达式、{@code AtomicBoolean} 节拍守卫都留在
 *       app 侧（{@code AsyncTaskPoller}）。「这个进程要不要跑定时任务」是部署问题，
 *       与「异步任务怎么推进」无关。</li>
 *   <li><b>不发 HTTP</b>：查询走 {@link AsyncTaskQueryPort}。换客户端、改签名、加渠道
 *       都不该动这里一行。</li>
 *   <li><b>不碰 Reactor</b>：并发由调用方用 {@code flatMap} 扇出，本类只提供
 *       「处理单条任务」的同步方法 {@link #process}。业务层知道线程模型就是分层倒退。</li>
 *   <li><b>不算钱</b>：用量由 {@link AsyncTaskSettlementPort#charge} 算（复用既有提取与计费管线），
 *       本类只负责<b>顺序</b>——先算钱、再落终态、最后发事件。</li>
 * </ul>
 *
 * <h3>两条硬规则（改了就是线上事故）</h3>
 * <ol>
 *   <li><b>占位是租约，不是锁</b>：拿到只是「这一轮由我来做」，<b>每一次写都要带着它</b>
 *       （{@code scheduleNext} / {@code complete} 的 {@code claim_id} 条件）。少了这一条，
 *       租约过期后原节点仍能写库：它会把新主人刚立的占位抹成 null，第三个节点随即再领一次同一行，
 *       重复处理就这么滚起来。并发最多到此为止——结果唯一性由 {@code complete} 的 CAS 兜底，
 *       租约只负责「不要把上游调用算两遍」。</li>
 *   <li><b>状态迁移只问状态机</b>：本类只「取出事实」+「把事实翻译成一个事件」，
 *       「该落哪个状态、要不要计费」一律问 {@link AsyncTaskStateMachine}。
 *       在这里补 {@code if (event == ...)} 会让状态机退化成装饰。</li>
 * </ol>
 */
@Slf4j
public class AsyncTaskRunner {

    private final AsyncTaskService taskService;
    private final AsyncTaskQueryPort queryPort;
    private final AsyncTaskSettlementPort settlementPort;

    /**
     * 产物转储；单测可为 {@code null}，此时直接用供应商地址（与 {@code passthrough} 同效）。
     *
     * <p>⚠ 生产环境不会是 null：默认实现由 {@code PassthroughArchiveStore} 兜底。
     * 这里允许 null 只是为了不让单测被迫构造一个转储器。</p>
     */
    @Nullable
    private final ArchiveStore archiveStore;

    private final AsyncTaskRunPolicy policy;

    /**
     * Constructs a new instance.
     */
    public AsyncTaskRunner(AsyncTaskService taskService,
                           AsyncTaskQueryPort queryPort,
                           AsyncTaskSettlementPort settlementPort,
                           @Nullable ArchiveStore archiveStore,
                           AsyncTaskRunPolicy policy) {
        this.taskService = taskService;
        this.queryPort = queryPort;
        this.settlementPort = settlementPort;
        this.archiveStore = archiveStore;
        this.policy = policy;
    }

    /**
     * 回收超时任务：先抢占，再逐条走状态机（{@link AsyncTaskEvent#TIMEOUT}）。
     *
     * <p>⭐ 不能再用一条批量 {@code UPDATE} 收掉：那会绕过状态机，也绕过 {@link #finish} 里的
     * 终态事件发布，结果是这笔任务在数仓里<b>永远没有 {@code task_terminal=true} 的那一条</b>——
     * 按终态口径算的计费与成功率都会漏掉它（提交与中间查询两条还在，反而更容易被误读成
     * 「任务卡住了」）。代价是每行一次 PK 更新而非一次批量更新，
     * 批量上限仍由 {@link AsyncTaskRunPolicy#expireBatchSize()} 控制。</p>
     *
     * <p>⭐ 也不能「读到就改」：读到的那一刻别的节点可能正在处理这行。先 {@code claimExpired}
     * 拿下租约，才敢说这行的终态由我定——否则本节点会用一个过期结论覆盖别人即将得出的真实结论。</p>
     *
     * @param claimId 本轮占位号（由发起方按节点身份生成：一个节点一台机器一条号，便于事后追溯）
     */
    public void expireTimedOut(String claimId) {
        List<AsyncTaskDto> expired = taskService.claimExpired(claimId,
                Duration.ofMillis(policy.claimTtlMs()), policy.expireBatchSize());
        if (expired.isEmpty()) {
            return;
        }
        log.warn("Async tasks expired before terminal state: count={}", expired.size());
        for (AsyncTaskDto task : expired) {
            AsyncTaskContext context = JsonKit.fromJson(task.contextJson(), AsyncTaskContext.class);
            // 用行上已发生的查询次数，不是 0：一个查了 50 次才等到超时的任务，
            // 终态事件里写「查询 0 次」会让延迟分布凭空少一截
            int querySeq = Math.max(0, task.attempts() == null ? 0 : task.attempts());
            apply(new Polling(task, context, null, querySeq, null, null), AsyncTaskEvent.TIMEOUT);
        }
    }

    /**
     * 抢占一批到期任务，返回「这一轮归我处理」的行。
     *
     * <p>⚠ 返回后<b>必须在租约有效期内处理完</b>（{@link AsyncTaskRunPolicy#claimTtlMs()}）：
     * 处理不完时行会被别的节点回收，本次的写入会被 CAS 挡掉，表现为「查了上游但没报数」。</p>
     */
    public List<AsyncTaskDto> claimDue(String claimId) {
        return taskService.claim(claimId, Duration.ofMillis(policy.claimTtlMs()), policy.batchSize());
    }

    /** 清理保留窗口之前的终态行（后台家务） */
    public int cleanup() {
        int removed = taskService.cleanup(
                LocalDateTime.now().minusDays(Math.max(1, policy.retentionDays())),
                policy.cleanupBatchSize());
        if (removed > 0) {
            log.info("Async tasks archived: removed={} retentionDays={}", removed, policy.retentionDays());
        }
        return removed;
    }

    /**
     * 在<b>已持有租约</b>的行上施加一个事件并落终态（运维动作的唯一执行口）。
     *
     * <p>⭐ <b>为什么运维动作也走这里，而不是让管理端直接 UPDATE 状态</b>：落终态不只是改一行，
     * 它还要（a）过状态机确认这个事件在当前离线语义下合法、（b）通过 {@code claim_id} 的 CAS 确认
     * 写入权、（c）发 {@code task_terminal=true} 的终态事件。管理端自己改库会跳过 (c)，
     * 于是这笔任务在数仓里<b>永远没有终态那条</b>——按终态口径算的计费与成功率都会漏掉它，
     * 而库里看起来一切正常。这条边界与 {@code expireTimedOut} 拒绝批量 UPDATE 的理由是同一条。</p>
     *
     * <p>⚠ <b>不查上游、不算钱</b>：{@link AsyncTaskEvent#CANCEL} 在状态机里是
     * {@code SETTLE}（不计费），这里也不该出现查询调用——运维终止的语义是「到此为止」，
     * 不是「再确认一次」。耗时与失败口径都由 {@code finish} 统一处理。</p>
     *
     * @param task  已经由 {@code claimById} / {@code claim} 抢到租约的行（其 {@code claimId} 必须是本次的）
     * @param event 要施加的事件；只接受会落终态的那几种，其他事件会在这里被判成 REJECT
     * @return 是否真的由本调用落定（{@code false} 表示租约已易主或行已终态）
     */
    public boolean settle(AsyncTaskDto task, AsyncTaskEvent event) {
        AsyncTaskContext context = StringUtils.isBlank(task.contextJson())
                ? null
                : JsonKit.fromJson(task.contextJson(), AsyncTaskContext.class);
        // 与 expireTimedOut 同口径：这次没有真的查上游，querySeq 取行上已有的次数而不是 0
        int querySeq = Math.max(0, task.attempts() == null ? 0 : task.attempts());
        return apply(new Polling(task, context, null, querySeq, null, null), event);
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
    public boolean process(AsyncTaskDto task) {
        AsyncTaskSpec spec = JsonKit.fromJson(task.pollSpecJson(), AsyncTaskSpec.class);
        AsyncTaskContext context = JsonKit.fromJson(task.contextJson(), AsyncTaskContext.class);
        if (spec == null || context == null) {
            log.error("Async task snapshot is unreadable, mark failed: taskCode={}", task.taskCode());
            return apply(new Polling(task, null, null, 0, null, null), AsyncTaskEvent.SNAPSHOT_INVALID);
        }
        int attempts = Math.max(0, task.attempts() == null ? 0 : task.attempts()) + 1;
        if (attempts > spec.maxAttempts()) {
            // 没有发出查询：querySeq 取行上已有的查询次数（本次只是一次「判定放弃」）
            return apply(new Polling(task, context, spec, attempts - 1, null, null),
                    AsyncTaskEvent.ATTEMPTS_EXHAUSTED);
        }
        String body;
        try {
            body = queryPort.query(context, spec, task.upstreamTaskId());
        } catch (RuntimeException e) {
            // 瞬时失败（网络 / 5xx / 限流）：退避后重试，不判终态
            log.warn("Async task query failed, will retry: taskCode={} attempts={}", task.taskCode(), attempts, e);
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
        return switch (AsyncTaskSpecRules.terminalOf(queryBody, spec)) {
            case RUNNING -> AsyncTaskEvent.POLL_RUNNING;
            case FAILED -> AsyncTaskEvent.POLL_FAILED;
            case SUCCEEDED -> StringUtils.isBlank(AsyncTaskSpecRules.resultUrlOf(queryBody, spec))
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
        AsyncTaskDto task = polling.task();
        AsyncTaskTransition transition = AsyncTaskStateMachine.transition(
                AsyncTaskStatusEnum.fromCode(task.status()), event);
        return switch (transition.action()) {
            case REJECT -> {
                // 不补发查询事件：这行已由别的节点发过终态事件，我们这次查询属重复劳动，
                // 记了反而让「上游调用数」虚高。宁可少记一次，也不让同一 task_code 出现两条终态。
                log.warn("Async task transition rejected, no state change: taskCode={} from={} event={}",
                        task.taskCode(), transition.from(), event);
                yield true;
            }
            case RESCHEDULE -> {
                long delay = AsyncTaskSpecRules.nextDelayMs(polling.attempts(), polling.spec());
                boolean advanced = taskService.scheduleNext(task.id(), task.claimId(),
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
                            task.taskCode(), polling.attempts(), task.claimId());
                }
                yield false;
            }
            case SETTLE_BILLED -> {
                List<String> resultUrls = AsyncTaskSpecRules.resultUrlsOf(polling.queryBody(), polling.spec());
                String sourceUrl = resultUrls.isEmpty() ? null : resultUrls.get(0);
                UsageAccounting usage = settlementPort.charge(task, polling.context(), polling.rawBody());
                // 先算钱再搬文件：搬是旁路，算钱是主账。两者互不依赖，但顺序反了会出现
                // 「文件已搬、计费抛异常、下次重查又搬一遍」——虽然按 taskCode 幂等不该产生副本，
                // 也没必要给实现方出这种题
                finish(task, polling.context(), transition.to(), polling.attempts(),
                        deliveredUrls(resultUrls, archive(task, polling.context(), sourceUrl)),
                        null, null, usage);
                yield true;
            }
            case SETTLE -> {
                finish(task, polling.context(), transition.to(), polling.attempts(), List.of(),
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
     * {@link #finish} 自己发终态事件。</p>
     */
    private void publishQuery(Polling polling, AsyncTaskEvent event) {
        if (polling.context() == null) {
            return;
        }
        try {
            settlementPort.publishQuery(polling.task(), polling.context(), polling.attempts(), event.errorCode());
        } catch (RuntimeException e) {
            // 观测旁路失败不能影响任务推进：这条丢了还有下一次查询
            log.error("Async task query event publish failed: taskCode={} seq={}",
                    polling.task().taskCode(), polling.attempts(), e);
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
     * （{@link #process} 在快照不可读时就提前返回了），故此处不做空判——真出现也会被下面的 catch 兜住。</p>
     */
    private String archive(AsyncTaskDto task, AsyncTaskContext context, String sourceUrl) {
        if (archiveStore == null || StringUtils.isBlank(sourceUrl)) {
            return sourceUrl;
        }
        try {
            String archived = archiveStore.archive(new ArchiveAsset(
                    task.taskCode(),
                    task.upstreamTaskId(),
                    sourceUrl,
                    context.modelCode(),
                    context.upstreamModelCode(),
                    context.providerAccountCode(),
                    context.ruleCode(),
                    LocalDateTime.now()));
            if (StringUtils.isBlank(archived)) {
                log.error("Video archive returned blank url, fall back to upstream: taskCode={} sourceUrl={}",
                        task.taskCode(), sourceUrl);
                return sourceUrl;
            }
            if (!StringUtils.equals(archived, sourceUrl)) {
                log.info("Video archived: taskCode={} url={}", task.taskCode(), archived);
            }
            return archived;
        } catch (RuntimeException e) {
            log.error("Video archive failed, fall back to upstream url: taskCode={} sourceUrl={}",
                    task.taskCode(), sourceUrl, e);
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
            // 档案配了 error_message_path 就按它取（视频协议常把原因放在 data.task_status_msg /
            // error.message，顶层 message 取不到）；没配或取不到再回落到顶层 message
            case POLL_FAILED -> StringUtils.defaultIfBlank(
                    StringUtils.defaultIfBlank(
                            AsyncTaskSpecRules.errorMessageOf(polling.queryBody(), polling.spec()),
                            upstreamMessage(polling.queryBody())),
                    event.defaultMessage());
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
    private record Polling(AsyncTaskDto task,
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
    private void finish(AsyncTaskDto task,
                        AsyncTaskContext context,
                        AsyncTaskStatusEnum status,
                        int querySeq,
                        List<String> resultUrls,
                        String errorCode,
                        String errorMessage,
                        UsageAccounting usage) {
        boolean settled = taskService.complete(task.id(), task.claimId(), status.code(),
                resultJson(resultUrls), errorCode, errorMessage,
                AsyncTaskDeliveryStatusEnum.PENDING.code());
        if (!settled) {
            log.warn("Async task not settled by this node (already settled or claim lost), "
                            + "terminal event skipped: taskCode={} status={} claimId={}",
                    task.taskCode(), status, task.claimId());
            return;
        }
        if (context == null) {
            return;
        }
        try {
            settlementPort.publishTerminal(task, context, querySeq, usage, errorCode, errorMessage);
        } catch (RuntimeException e) {
            // 事件发布失败不影响任务终态：钱已经算过了，事件是旁路观测
            log.error("Async task terminal event publish failed: taskCode={}", task.taskCode(), e);
        }
    }

    /**
     * 终态产物 JSON。
     *
     * <p>⚠ <b>两个键不是冗余</b>：{@code result_url} 是既有对外契约（客户端按它取产物，
     * 归档替换后的地址也写在这里），{@code result_urls} 是 2026-10-29 新增的多产物全集
     * （视频协议一次生成 N 个）。只写数组会破既有客户端，只写单值会丢产物——
     * 少交付一个视频比多一个键严重得多。</p>
     */
    private String resultJson(List<String> resultUrls) {
        Map<String, Object> result = new LinkedHashMap<>();
        resultUrls.stream().findFirst().ifPresent(url -> result.put("result_url", url));
        result.put("result_urls", resultUrls);
        return JsonKit.toJson(result);
    }

    /**
     * 首个产物换成归档地址（其余保留上游地址）；归档未启用时原样返回。
     *
     * <p>只换第一个是因为 {@code ArchiveStore} 是单产物旁路：它按任务号搬一个文件，
     * 把第 2..N 个也传进去会让同一个 taskCode 产生多个归档副本，而归档本身是按任务号幂等的。</p>
     */
    private static List<String> deliveredUrls(List<String> resultUrls, String archivedUrl) {
        if (resultUrls.isEmpty() || StringUtils.isBlank(archivedUrl)
                || StringUtils.equals(archivedUrl, resultUrls.get(0))) {
            return resultUrls;
        }
        List<String> delivered = new ArrayList<>(resultUrls);
        delivered.set(0, archivedUrl);
        return List.copyOf(delivered);
    }

    private String upstreamMessage(Map<String, Object> queryBody) {
        Object message = queryBody == null ? null : queryBody.get("message");
        return message == null ? null : StringUtils.abbreviate(String.valueOf(message), 1000);
    }
}
