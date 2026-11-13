package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.chobit.knot.common.constants.enums.AsyncTaskDeliveryStatusEnum;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.archive.ArchiveAsset;
import org.chobit.knot.gateway.archive.ArchiveStore;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskTerminalDto;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步任务的编排：抢占 → 查询 → 判定 → 走状态机 → 落终态/计费/发事件。
 * 边界：不做定时（{@code @Scheduled} / cron / 节拍守卫留在 app 侧 {@code AsyncTaskPoller}）、不发 HTTP（查询走 {@link AsyncTaskQueryPort}）、不碰 Reactor（只提供同步方法 {@link #process}）。
 * 不算钱：用量由 {@link AsyncTaskSettlementPort#charge} 算，本类只保证「先算钱、再落终态、最后发事件」的顺序。
 * 占位是租约不是锁：每次写都带 {@code claim_id} 条件（{@code scheduleNext / complete}），结果唯一性由 {@code complete} 的 CAS 兜底；状态迁移只问 {@link AsyncTaskStateMachine}。
 */
@Slf4j
public class AsyncTaskRunner {

    private final AsyncTaskService taskService;
    private final AsyncTaskQueryPort queryPort;
    private final AsyncTaskSettlementPort settlementPort;

    /**
     * 产物转储；单测可为 {@code null}，此时直接用供应商地址（与 {@code passthrough} 同效）。
     * 生产环境由默认实现 {@code PassthroughArchiveStore} 兜底，不会是 null。
     */
    @Nullable
    private final ArchiveStore archiveStore;

    private final AsyncTaskRunPolicy policy;

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
     * 逐条走状态机而非一条批量 {@code UPDATE}（每行一次 PK 更新），批量上限由 {@link AsyncTaskRunPolicy#expireBatchSize()} 控制。
     * 先 {@code claimExpired} 拿下租约再定终态，不「读到就改」。
     *
     * @param claimId 本轮占位号（由发起方按节点身份生成：一个节点一台机器一条号，便于事后追溯）
     */
    public void expireTimedOut(String claimId) {
        List<AsyncTaskDto> expired =
            taskService.claimExpired(claimId, Duration.ofMillis(policy.claimTtlMs()), policy.expireBatchSize());

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
     * 抢占一批到期任务，返回「这一轮归我处理」的行；返回后必须在租约有效期（{@link AsyncTaskRunPolicy#claimTtlMs()}）内处理完。
     */
    public List<AsyncTaskDto> claimDue(String claimId) {
        return taskService.claim(claimId, Duration.ofMillis(policy.claimTtlMs()), policy.batchSize());
    }

    /**
     * 清理保留窗口之前的终态行（后台家务）
     */
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
     * 在已持有租约的行上施加一个事件并落终态（运维动作的唯一执行口）。
     * 落终态要（a）过状态机确认事件合法、（b）通过 {@code claim_id} 的 CAS 确认写入权、（c）发 {@code task_terminal=true} 的终态事件。
     * 不查上游、不算钱：{@link AsyncTaskEvent#CANCEL} 在状态机里是 {@code SETTLE}（不计费）；耗时与失败口径由 {@code finish} 统一处理。
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
     * 发起上游查询前先把状态置为 {@code POLLING}。
     * 只把事实取出来（快照、次数、上游响应）并翻译成一个事件；该落哪个状态、要不要计费一律问 {@link AsyncTaskStateMachine}。
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
        // 发起查询之前先离开 PENDING：0 的语义是「提交后一次查询都没发起过」，
        // 而「首查即终态」的任务会从 0 直接落 2——不在这里先置 1，
        // 运维就永远分不清「poller 没接手」和「已接手、查过了」。
        // 写不进（租约易主 / 已终态）不影响本次推进：真正的兑付口是后面的
        // complete / scheduleNext，它们各自的 CAS 会把关。
        taskService.markPolling(task.id(), task.claimId());
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
     * 上游查询结果 → 事件（唯一的语义判定点）。
     * 只回答「上游说了什么」，不决定「落哪个状态」；三值必须穷尽，「上游说成功但取不到产物」是独立事件（{@link AsyncTaskEvent#RESULT_MISSING}）。
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
     * 状态机结果的唯一执行口：写库、计费、发事件都从这里出去。
     * 严格按 {@link AsyncTaskTransition.Action} 分派，不做二次判断。
     */
    private boolean apply(Polling polling, AsyncTaskEvent event) {
        AsyncTaskDto task = polling.task();
        AsyncTaskTransition transition = AsyncTaskStateMachine.transition(AsyncTaskStatusEnum.fromCode(task.status()), event);
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
                    null, null, usage, polling.rawBody());
                yield true;
            }
            case SETTLE -> {
                finish(task, polling.context(), transition.to(), polling.attempts(), List.of(),
                    event.errorCode(), messageOf(polling, event), null, polling.rawBody());
                yield true;
            }
        };
    }

    /**
     * 发布一次中间查询事件（非终态）。
     * 只在 {@code RESCHEDULE} 分支调用；落终态的分支由 {@link #finish} 自己发终态事件。
     */
    private void publishQuery(Polling polling, AsyncTaskEvent event) {
        if (polling.context() == null) {
            return;
        }
        try {
            settlementPort.publishQuery(polling.task(), polling.context(), polling.attempts(), event.errorCode());
        } catch (RuntimeException e) {
            // 观测旁路失败不能影响任务推进：这条丢了还有下一次查询
            log.error("Async task query event publish failed: taskCode={} seq={}", polling.task().taskCode(), polling.attempts(), e);
        }
    }

    /**
     * 把产物搬到可控位置，返回客户端最终拿到的地址；失败一律回退供应商原地址。
     * 转储不是交付前置条件：挂在「上游说成功、产物 URL 已取到」之后，任务照常落 {@code SUCCEEDED} 并计费。
     * 三种回退（未配转储器 / 转储返回空白 / 转储抛异常）都记 ERROR 且不向上抛，依据日志里的 {@code taskCode} 与 {@code sourceUrl} 事后补搬。
     * 只从 {@code SETTLE_BILLED} 调用，该路径 {@code context} 必非 null（{@link #process} 在快照不可读时已提前返回），此处不做空判。
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
     * 只管「怎么说」，不管「去哪儿」（由状态机决定）。
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
     * 一次轮询采集到的事实，攒成一个 record。
     *
     * @param attempts 本次查询是第几次（写进事件的 {@code task_query_seq}，也用于退避计算）。
     *                 没有真的发出查询的事件（快照不可读、次数用尽、knot 侧超时）传的是
     *                 行上已有的查询次数，不是 +1——它们不代表又查了一次上游
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
     * CAS 条件是「在途态 + 仍持有占位」：分别挡「别人已落终态」与「本轮超时、行已回收」；两者都表现为 {@code complete} 返回 false，写不进的行不再由本节点报数。
     *
     * @param querySeq     本次任务一共查了几次上游；写进终态事件的 {@code task_query_seq}，
     *                     它是「产物要等多久」的唯一量化口径（与提交事件配对即得延迟分布）
     * @param upstreamBody 本次查询拿到的上游响应原文；没有发出查询（超时 / 快照不可读）时为
     *                     {@code null}。与结算快照一起落库，理由见 {@link AsyncTaskTerminalDto}
     */
    private void finish(AsyncTaskDto task,
                        AsyncTaskContext context,
                        AsyncTaskStatusEnum status,
                        int querySeq,
                        List<String> resultUrls,
                        String errorCode,
                        String errorMessage,
                        UsageAccounting usage,
                        String upstreamBody) {
        boolean settled = taskService.complete(task.id(), task.claimId(), status.code(),
            resultJson(resultUrls), terminalEvidenceOf(upstreamBody, usage),
            errorCode, errorMessage,
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
        if (usage != null) {
            try {
                settlementPort.recordUsage(task, context, usage);
            } catch (RuntimeException e) {
                // 任务已经落终态，不能为了补计数把它再挂回在途；丢计数必须留错误日志供对账。
                log.error("Async task usage record failed: taskCode={}", task.taskCode(), e);
            }
        }
        try {
            settlementPort.publishTerminal(task, context, querySeq, usage, errorCode, errorMessage);
        } catch (RuntimeException e) {
            // 事件发布失败不影响任务终态：钱已经算过了，事件是旁路观测
            log.error("Async task terminal event publish failed: taskCode={}", task.taskCode(), e);
        }
    }

    /**
     * 终态证据：上游原文 + 结算快照，与终态同一条 {@code UPDATE} 落库。
     * 结算快照复用 {@link KnotExtendPayload}（同步链路 {@code knot_extend} 的同一类型），异步与同步在存储层同形。
     * 不计费的终态（失败 / 超时 / 取消）{@code usage == null} → 结算段为 {@code null}，不写零账。
     */
    private static AsyncTaskTerminalDto terminalEvidenceOf(String upstreamBody, UsageAccounting usage) {
        if (usage == null) {
            return new AsyncTaskTerminalDto(upstreamBody, null);
        }
        KnotExtendPayload payload = KnotExtendPayload.of(usage);
        // ⚠ 空账（isEmpty）时 of() 返回 null，此时必须落 SQL NULL 而不是 JsonKit.toJson(null)
        // ——后者会把字面量 "null" 写进 settlement_json，读出来是「有账目、内容为 null」，
        // 而事实是「这次没有账目」。两者在「成功必有账」那条自检里的结论正好相反。
        return new AsyncTaskTerminalDto(upstreamBody, payload == null ? null : JsonKit.toJson(payload));
    }

    /**
     * 终态产物 JSON。
     * {@code result_url} 是既有对外契约（客户端按它取产物，归档替换后的地址也写在这里）；{@code result_urls} 是多产物全集（视频协议一次生成 N 个）。
     */
    private String resultJson(List<String> resultUrls) {
        Map<String, Object> result = new LinkedHashMap<>();
        resultUrls.stream().findFirst().ifPresent(url -> result.put("result_url", url));
        result.put("result_urls", resultUrls);
        return JsonKit.toJson(result);
    }

    /**
     * 首个产物换成归档地址（其余保留上游地址）；归档未启用时原样返回。
     * 只换第一个：{@code ArchiveStore} 是单产物旁路，按任务号搬一个文件。
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
