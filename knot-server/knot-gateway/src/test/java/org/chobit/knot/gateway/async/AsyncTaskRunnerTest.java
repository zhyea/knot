package org.chobit.knot.gateway.async;

import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.archive.ArchiveAsset;
import org.chobit.knot.gateway.archive.ArchiveStore;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：异步任务的编排（{@link AsyncTaskRunner}）。
 *
 * <p>⭐ 这里守的是「一条任务一条终态、且不重复计费」：终态 CAS 失败不发事件、
 * 非终态不落终态、成功但无产物按失败处理。三条任一失守都会直接变成资损或对不上账。</p>
 *
 * <p>⭐ 多节点那一半守在「占位号必须跟着每一次写」：{@code scheduleNext} / {@code complete}
 * 都带 {@code claim-1}，写不进去就不报数。这些断言没法证明并发正确（那要靠 SQL 的行锁），
 * 能证明的是<b>调用方没把租约丢掉</b>——租约条件漏在调用方这一侧是最常见的失守方式。</p>
 *
 * <p>⚠ <b>本类由 {@code AsyncTaskPollerTest} 拆出来</b>（2026-10-29 编排与节拍分离）：
 * 「什么时候扫、同时扫几个」留在网关测（{@code AsyncTaskPollerTest}），
 * 「扫到之后做什么」跟着代码搬到这里。拆的时候没有删任何一条用例——
 * 能力搬家时测试必须跟着搬，否则就是「删了能力测试还绿」。</p>
 */
class AsyncTaskRunnerTest {

    private static final String CLAIM_ID = "claim-1";

    /** 策略取值与生产默认值同口径（裸字面量：改 {@code AsyncTaskDefaults} 不该让这里静默跟着变） */
    private static final AsyncTaskRunPolicy POLICY =
            new AsyncTaskRunPolicy(100, 200, 180_000L, 30, 500);

    private RecordingTaskService taskService;
    private RecordingQueryClient queryClient;
    private RecordingSettlement settlement;
    private RecordingArchiveStore archiveStore;
    private AsyncTaskRunner service;

    @BeforeEach
    void setUp() {
        taskService = new RecordingTaskService();
        queryClient = new RecordingQueryClient();
        settlement = new RecordingSettlement();
        archiveStore = new RecordingArchiveStore();
        service = new AsyncTaskRunner(taskService, queryClient, settlement, archiveStore, POLICY);
    }

    @Test
    @DisplayName("终态成功：计费 → 落 SUCCEEDED → 发一条终态事件")
    void successSettlesAndPublishesOnce() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://x/a.mp4\"}]}]}";

        assertTrue(service.process(task));

        CompleteCall settled = taskService.onlyComplete();
        assertEquals(7L, settled.id());
        assertEquals(CLAIM_ID, settled.claimId());
        assertEquals(AsyncTaskStatusEnum.SUCCEEDED.code(), settled.status());
        assertNull(settled.errorCode());
        assertNull(settled.errorMessage());
        assertEquals(1, settlement.charged, "终态必须且只能计一次费");
        assertEquals(1, settlement.published, "终态必须且只能发一条事件");
        assertEquals(1, settlement.terminalSeq, "首次查询就拿到终态，seq 应为 1");
        assertEquals(0, settlement.queries, "落终态那一拍不该再单独发中间事件（同一次查询只发一条）");
        assertEquals(0, taskService.schedules.size(), "落终态那一拍不该再安排下一次轮询");
    }

    @Test
    @DisplayName("终态成功：产物转储一次，落库的是转储后的地址")
    void successArchivesOnce() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://up/a.mp4\"}]}]}";
        archiveStore.result = "https://s3/a.mp4";

        assertTrue(service.process(task));

        assertEquals(1, archiveStore.archived.size(), "终态只搬一次，不能在中间态尝试");
        ArchiveAsset asset = archiveStore.archived.get(0);
        assertEquals("tc-1", asset.taskCode(), "taskCode 是实现方做幂等键的唯一依据");
        assertEquals("up-1", asset.upstreamTaskId());
        assertEquals("https://up/a.mp4", asset.sourceUrl(), "转储吃的是供应商地址，不是我们自己的");
        assertEquals("kling-3", asset.modelCode());
        assertEquals("rule-1", asset.ruleCode());
        CompleteCall settled = taskService.onlyComplete();
        assertEquals(AsyncTaskStatusEnum.SUCCEEDED.code(), settled.status());
        assertEquals("{\"result_url\":\"https://s3/a.mp4\"}", settled.resultJson(),
                "落库的必须是转储后的地址");
        assertNull(settled.errorCode());
        assertEquals(1, settlement.charged);
    }

    @Test
    @DisplayName("⭐ 转储失败回退供应商地址：任务照样成功、照样计费")
    void archiveFailureFallsBackToUpstreamUrl() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://up/a.mp4\"}]}]}";
        archiveStore.error = new IllegalStateException("s3 down");

        assertTrue(service.process(task));

        CompleteCall settled = taskService.onlyComplete();
        assertEquals(AsyncTaskStatusEnum.SUCCEEDED.code(), settled.status());
        assertEquals("{\"result_url\":\"https://up/a.mp4\"}", settled.resultJson(),
                "回退到供应商地址也必须落库");
        assertEquals(1, settlement.charged, "转储是旁路，不能因为搬文件失败就不算钱");
        assertEquals(1, settlement.published, "终态事件也不能少发——否则数仓看不到这笔成功");
    }

    @Test
    @DisplayName("转储返回空白按失败处理：不把空 URL 交给客户端")
    void blankArchivedUrlFallsBack() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://up/a.mp4\"}]}]}";
        archiveStore.result = "   ";

        assertTrue(service.process(task));

        CompleteCall settled = taskService.onlyComplete();
        assertEquals(AsyncTaskStatusEnum.SUCCEEDED.code(), settled.status());
        assertEquals("{\"result_url\":\"https://up/a.mp4\"}", settled.resultJson(),
                "回退到供应商地址也必须落库");
    }

    @Test
    @DisplayName("上游失败不转储：没有产物可搬")
    void failedTaskNeverArchives() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"failed\"}],\"message\":\"prompt rejected\"}";

        assertTrue(service.process(task));

        assertEquals(0, archiveStore.archived.size(), "没有产物时不该调用转储");
    }

    @Test
    @DisplayName("上游任务失败：按 FAILED 落终态，不计费")
    void upstreamFailureNeverBills() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"failed\"}],\"message\":\"prompt rejected\"}";

        assertTrue(service.process(task));

        CompleteCall settled = taskService.onlyComplete();
        assertEquals(AsyncTaskStatusEnum.FAILED.code(), settled.status());
        assertEquals("UPSTREAM_TASK_FAILED", settled.errorCode());
        assertEquals("prompt rejected", settled.errorMessage(), "话术优先取上游给的 message");
        assertEquals(0, settlement.charged, "上游失败不计费");
        assertEquals(1, settlement.published, "失败也要发终态事件（观测链路不能断）");
    }

    @Test
    @DisplayName("成功但取不到产物：按失败落终态，不计费（钱收了没有货）")
    void successWithoutResultIsFailure() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\"}]}";

        assertTrue(service.process(task));

        CompleteCall settled = taskService.onlyComplete();
        assertEquals(AsyncTaskStatusEnum.FAILED.code(), settled.status());
        assertEquals("UPSTREAM_RESULT_MISSING", settled.errorCode());
        assertEquals(0, settlement.charged, "没有产物绝不能计入金额");
    }

    @Test
    @DisplayName("非终态：安排下一次轮询，不落终态、不计费，但要发一条中间查询事件")
    void runningSchedulesNextOnly() {
        taskService.scheduleNextResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"processing\"}]}";

        assertFalse(service.process(task));

        ScheduleCall scheduled = taskService.onlySchedule();
        assertEquals(7L, scheduled.id());
        assertEquals(CLAIM_ID, scheduled.claimId());
        assertEquals(0, taskService.completes.size(), "这一拍不该落终态");
        assertEquals(0, settlement.charged);
        assertEquals(0, settlement.published, "还没到终态，不能发终态事件");
        assertEquals(List.of(1), settlement.querySeqs,
                "这次查询确实打到了上游，必须留一条 task_phase=query 的事件");
    }

    @Test
    @DisplayName("⭐ 占位已易主：写不进下一次轮询，就不报这一条查询事件")
    void lostClaimSkipsQueryEvent() {
        // 桩默认 scheduleNextResult=false，正是「行已被别的节点回收」的形态
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"processing\"}]}";

        assertFalse(service.process(task));

        assertEquals(1, queryClient.calls, "上游确实被调了一次（成本已发生，只是记不到这一行上）");
        assertEquals(0, settlement.queries,
                "这一行的 query_seq 已归新主人报：两边都报会让数仓无从判断哪条才是真的");
    }

    @Test
    @DisplayName("⭐ 一次异步任务 = 1 次提交 + N 次查询：每查一次发一条，seq 单调递增，终态那条带 terminal")
    void eachQueryLeavesOneEvent() {
        AsyncTaskSpec spec = spec();
        taskService.scheduleNextResult = true;
        queryClient.body = "{\"data\":[{\"status\":\"processing\"}]}";

        service.process(task(0, JsonKit.toJson(spec), JsonKit.toJson(context())));
        service.process(task(1, JsonKit.toJson(spec), JsonKit.toJson(context())));

        taskService.completeResult = true;
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://x/a.mp4\"}]}]}";
        service.process(task(2, JsonKit.toJson(spec), JsonKit.toJson(context())));

        assertEquals(List.of(1, 2), settlement.querySeqs,
                "两次非终态查询各留一条，且 seq 从 1 开始递增");
        assertEquals(1, settlement.published);
        assertEquals(3, settlement.terminalSeq, "第三次查询才拿到产物，终态事件必须自报 seq=3");
    }

    @Test
    @DisplayName("查询瞬时失败：退避后重试，不判终态，但中间事件要带上错误码")
    void queryErrorRetriesWithBackoff() {
        taskService.scheduleNextResult = true;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.error = new IllegalStateException("connection reset");

        assertFalse(service.process(task));

        ScheduleCall scheduled = taskService.onlySchedule();
        assertEquals(7L, scheduled.id());
        assertEquals(CLAIM_ID, scheduled.claimId());
        assertTrue(scheduled.nextPollAt().isAfter(LocalDateTime.now().minusSeconds(1)),
                "退避后的下一次轮询必须在未来，否则会变成忙等");
        assertEquals(0, taskService.completes.size(), "这一拍不该落终态");
        assertEquals(List.of(1), settlement.querySeqs,
                "失败那次也真实打了上游，不能因为没拿到响应就不记");
    }

    @Test
    @DisplayName("轮询次数用尽：转 EXPIRED，不再打上游")
    void attemptsExhaustedExpires() {
        taskService.completeResult = true;
        AsyncTaskSpec spec = spec();
        AsyncTaskDto task = task(spec.maxAttempts(), JsonKit.toJson(spec), JsonKit.toJson(context()));

        assertTrue(service.process(task));

        assertEquals(0, queryClient.calls, "次数用尽后不应再消耗上游查询额度");
        CompleteCall settled = taskService.onlyComplete();
        assertEquals(AsyncTaskStatusEnum.EXPIRED.code(), settled.status());
        assertEquals("TASK_TIMEOUT", settled.errorCode());
        assertEquals(0, taskService.schedules.size(), "落终态那一拍不该再安排下一次轮询");
        assertEquals(0, settlement.queries, "没打出这次查询，就不能凭空记一条");
        assertEquals(spec.maxAttempts(), settlement.terminalSeq,
                "终态事件要如实报「一共查了几次」，不能因为最后一次没查就归零");
    }

    @Test
    @DisplayName("快照不可解析：按失败落终态，不静默留在在途态")
    void unreadableSnapshotFails() {
        taskService.completeResult = true;
        AsyncTaskDto task = task(0, "{oops", JsonKit.toJson(context()));

        assertTrue(service.process(task));
        CompleteCall settled = taskService.onlyComplete();
        assertEquals(AsyncTaskStatusEnum.FAILED.code(), settled.status());
        assertEquals("TASK_SNAPSHOT_INVALID", settled.errorCode());
    }

    @Test
    @DisplayName("终态 CAS 抢不到：不发事件（一条任务一条终态消息）")
    void alreadySettledSkipsEvent() {
        taskService.completeResult = false;
        AsyncTaskDto task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://x/a.mp4\"}]}]}";

        assertTrue(service.process(task));

        assertEquals(1, settlement.charged, "计费照做：金额是这一笔的账，与事件是否发出无关");
        assertEquals(0, settlement.published, "已被别的节点落定的任务不得再发一条终态事件");
    }

    @Test
    @DisplayName("⭐ 超时回收先占位：用本轮的占位号落 EXPIRED，并补发一条终态事件")
    void expireTimedOutClaimsThenSettles() {
        AsyncTaskDto expiredTask = task(2, JsonKit.toJson(spec()), JsonKit.toJson(context()), "claim-expired");
        taskService.claimExpiredResult = List.of(expiredTask);
        taskService.completeResult = true;

        service.expireTimedOut(CLAIM_ID);

        assertEquals(1, taskService.expiredClaims.size());
        ClaimCall expiredClaim = taskService.expiredClaims.get(0);
        assertEquals(Duration.ofMillis(POLICY.claimTtlMs()), expiredClaim.ttl(),
                "超时回收同样要按「一次处理的预算」占位，不能把租约压成 0");
        assertEquals(POLICY.expireBatchSize(), expiredClaim.limit());
        CompleteCall settled = taskService.onlyComplete();
        assertEquals("claim-expired", settled.claimId(), "必须用本轮的占位号落终态");
        assertEquals(AsyncTaskStatusEnum.EXPIRED.code(), settled.status());
        assertEquals("TASK_TIMEOUT", settled.errorCode());
        assertEquals(0, settlement.charged, "超时不计费");
        assertEquals(1, settlement.published,
                "超时也是一次状态转移，不补发终态事件的话这笔任务在数仓里就完全不存在");
    }

    @Test
    @DisplayName("没有超时任务时不落任何终态")
    void expireWithoutCandidatesWritesNothing() {
        taskService.claimExpiredResult = List.of();

        service.expireTimedOut(CLAIM_ID);

        assertEquals(0, taskService.completes.size(), "这一拍不该落终态");
    }

    @Test
    @DisplayName("清理按保留窗口删除：窗口外一天，不是「最近一天」")
    void cleanupUsesRetentionWindow() {
        taskService.cleanupResult = 3;

        assertEquals(3, service.cleanup());

        assertEquals(1, taskService.cleanups.size());
        CleanupCall cleanup = taskService.cleanups.get(0);
        assertEquals(POLICY.cleanupBatchSize(), cleanup.limit());
        assertTrue(cleanup.before().isBefore(LocalDateTime.now().minusDays(29)),
                "保留 30 天 = 删除时刻早于「现在减 30 天」；算成减 1 天会把历史全清掉");
    }

    /**
     * 存储桩：{@link AsyncTaskService} 是具体类（直连 Mapper），所以用<b>继承覆写</b>而不是 mock。
     *
     * <p>⚠ <b>为什么不用 Mockito</b>：本机 JDK 25 下 Mockito 的 inline mock maker 无法
     * instrument 具体类（{@code Byte Buddy could not instrument all classes within the mock's
     * type hierarchy}）。这与项目既有定式一致——编排层的 {@code AsyncTaskRunner} 在
     * {@code AsyncTaskPollerTest} 里也是子类覆写。</p>
     *
     * <p>默认值与 Mockito 的默认一致（{@code false} / {@code 0} / 空列表），
     * 于是「占位已易主、写不进去」这种边界不需要显式设置就是真实形态；
     * 需要写成功的用例逐个显式打开。</p>
     */
    private static final class RecordingTaskService extends AsyncTaskService {

        private final List<CompleteCall> completes = new ArrayList<>();
        private final List<ScheduleCall> schedules = new ArrayList<>();
        private final List<ClaimCall> claims = new ArrayList<>();
        private final List<ClaimCall> expiredClaims = new ArrayList<>();
        private final List<CleanupCall> cleanups = new ArrayList<>();

        private boolean completeResult;
        private boolean scheduleNextResult;
        private List<AsyncTaskDto> claimResult = List.of();
        private List<AsyncTaskDto> claimExpiredResult = List.of();
        private int cleanupResult;

        private RecordingTaskService() {
            // 覆写掉的方法都不碰 Mapper，传 null 安全
            super(null);
        }

        @Override
        public List<AsyncTaskDto> claim(String claimId, Duration claimTtl, int limit) {
            claims.add(new ClaimCall(claimId, claimTtl, limit));
            return claimResult;
        }

        @Override
        public List<AsyncTaskDto> claimExpired(String claimId, Duration claimTtl, int limit) {
            expiredClaims.add(new ClaimCall(claimId, claimTtl, limit));
            return claimExpiredResult;
        }

        @Override
        public boolean scheduleNext(Long id, String claimId, LocalDateTime nextPollAt) {
            schedules.add(new ScheduleCall(id, claimId, nextPollAt));
            return scheduleNextResult;
        }

        @Override
        public boolean complete(Long id, String claimId, int status, String resultJson,
                               String errorCode, String errorMessage) {
            completes.add(new CompleteCall(id, claimId, status, resultJson, errorCode, errorMessage));
            return completeResult;
        }

        @Override
        public int cleanup(LocalDateTime before, int limit) {
            cleanups.add(new CleanupCall(before, limit));
            return cleanupResult;
        }

        /** 「必须且只能落一次终态」的断言入口：顺手把「落了几次」也钉住 */
        private CompleteCall onlyComplete() {
            assertEquals(1, completes.size(), "期望落一次终态");
            return completes.get(0);
        }

        private ScheduleCall onlySchedule() {
            assertEquals(1, schedules.size(), "期望只安排一次下一次轮询");
            return schedules.get(0);
        }
    }

    private record CompleteCall(Long id, String claimId, int status, String resultJson,
                                String errorCode, String errorMessage) {
    }

    private record ScheduleCall(Long id, String claimId, LocalDateTime nextPollAt) {
    }

    private record ClaimCall(String claimId, Duration ttl, int limit) {
    }

    private record CleanupCall(LocalDateTime before, int limit) {
    }

    private static AsyncTaskSpec spec() {
        return new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.id", "data[0].status",
                "['succeed','SUCCEEDED'] contains status", "status == 'failed'", null,
                "data[0].outputs[0].url",
                7, 5000, 1000, 30000, 360, 1_800_000L);
    }

    private static AsyncTaskContext context() {
        return new AsyncTaskContext("tc-1", "rule-1", null, null, "req-1",
                "VIDEO_GENERATIONS", "kling-3", "kling-3-turbo", "kling", "pool-1", "https://x/videos");
    }

    /**
     * 带占位号的行：生产里 {@code claim} 回查出来的行一定带着它，
     * 桩里也照此造——否则测出来的「写库成功」在真实环境根本不会发生。
     */
    private static AsyncTaskDto task(int attempts, String specJson, String contextJson) {
        return task(attempts, specJson, contextJson, CLAIM_ID);
    }

    /**
     * ⚠ 占位号做成参数而不是造完再改：{@code AsyncTaskDto} 是不可变 record，
     * 「先造一个默认任务再改占位号」的写法不存在了。这反而更好——
     * 「租约易主」那条用例必须显式写出一个<b>不同于</b>当前占位的号，不能靠默认。
     */
    private static AsyncTaskDto task(int attempts, String specJson, String contextJson, String claimId) {
        return new AsyncTaskDto(7L, "tc-1", "KLING_TASK", null, "up-1",
                AsyncTaskStatusEnum.PENDING.code(), attempts, null, claimId,
                "{\"prompt\":\"a cat\"}", specJson, contextJson,
                null, null, null, null, null, null, null, null, null);
    }

    /**
     * 转储桩：记录被搬过的素材，按测试意愿返回新地址 / 抛错 / 返回空白。
     *
     * <p>默认行为与生产默认实现（{@code passthrough}）一致——原样返回 {@code sourceUrl}，
     * 于是没显式设置 {@code result} 的用例都在验「没配转储时的行为」。</p>
     */
    private static final class RecordingArchiveStore implements ArchiveStore {

        private final List<ArchiveAsset> archived = new java.util.ArrayList<>();
        private String result;
        private RuntimeException error;

        @Override
        public String archive(ArchiveAsset asset) {
            archived.add(asset);
            if (error != null) {
                throw error;
            }
            return result == null ? asset.sourceUrl() : result;
        }
    }

    /**
     * 查询桩：只记录调用次数与返回固定响应体（或抛错）。
     *
     * <p>⭐ 它是<b>端口</b>的实现而不是子类覆写——这正是端口化的收益：测试不再需要
     * 继承一个带 5 个构造参数的网关类。</p>
     */
    private static final class RecordingQueryClient implements AsyncTaskQueryPort {

        private String body = "{}";
        private RuntimeException error;
        private int calls;

        @Override
        public String query(AsyncTaskContext context, AsyncTaskSpec spec, String upstreamTaskId) {
            calls++;
            if (error != null) {
                throw error;
            }
            return body;
        }
    }

    /** 只数「计了几次费 / 发了几条终态事件 / 发了几条中间查询事件」。 */
    private static final class RecordingSettlement implements AsyncTaskSettlementPort {

        private int charged;
        private int published;
        private int queries;
        private final List<Integer> querySeqs = new java.util.ArrayList<>();
        private int terminalSeq = -1;

        @Override
        public UsageAccounting charge(AsyncTaskDto task, AsyncTaskContext context, String queryBody) {
            charged++;
            return UsageAccounting.empty();
        }

        @Override
        public void publishQuery(AsyncTaskDto task, AsyncTaskContext context, int querySeq, String errorCode) {
            queries++;
            querySeqs.add(querySeq);
        }

        @Override
        public void publishTerminal(AsyncTaskDto task, AsyncTaskContext context,
                                    int querySeq, UsageAccounting usage, String errorCode, String errorMessage) {
            published++;
            terminalSeq = querySeq;
        }
    }
}
