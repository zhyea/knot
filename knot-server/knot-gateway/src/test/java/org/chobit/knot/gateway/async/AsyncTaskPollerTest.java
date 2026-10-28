package org.chobit.knot.gateway.async;

import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.gateway.archive.ArchiveAsset;
import org.chobit.knot.gateway.archive.ArchiveStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 契约测试：轮询器的单次处理（{@link AsyncTaskPoller#process}）。
 *
 * <p>⭐ 这里守的是「一条任务一条终态、且不重复计费」：终态 CAS 失败不发事件、
 * 非终态不落终态、成功但无产物按失败处理。三条任一失守都会直接变成资损或对不上账。</p>
 *
 * <p>⭐ 多节点那一半守在「占位号必须跟着每一次写」：{@code scheduleNext} / {@code complete}
 * 都带 {@code claim-1}，写不进去就不报数。这些断言没法证明并发正确（那要靠 SQL 的行锁），
 * 能证明的是<b>调用方没把租约丢掉</b>——租约条件漏在调用方这一侧是最常见的失守方式。</p>
 */
class AsyncTaskPollerTest {

    private static final String CLAIM_ID = "claim-1";

    private AsyncTaskProperties properties;
    private AsyncTaskStore store;
    private AsyncTaskSupport support;
    private RecordingQueryClient queryClient;
    private RecordingCompletion completion;
    private RecordingArchiveStore archiveStore;
    private AsyncTaskPoller poller;

    @BeforeEach
    void setUp() {
        properties = new AsyncTaskProperties();
        properties.setNodeId("node-test");
        store = mock(AsyncTaskStore.class);
        support = new AsyncTaskSupport(null, null, store);
        queryClient = new RecordingQueryClient();
        completion = new RecordingCompletion();
        archiveStore = new RecordingArchiveStore();
        poller = new AsyncTaskPoller(properties, store, support, queryClient, completion, null, archiveStore);
        poller.init();
    }

    @Test
    @DisplayName("终态成功：计费 → 落 SUCCEEDED → 发一条终态事件")
    void successSettlesAndPublishesOnce() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), isNull(), isNull())).thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://x/a.mp4\"}]}]}";

        assertTrue(poller.process(task));

        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), isNull(), isNull());
        assertEquals(1, completion.accounted, "终态必须且只能计一次费");
        assertEquals(1, completion.published, "终态必须且只能发一条事件");
        assertEquals(1, completion.terminalSeq, "首次查询就拿到终态，seq 应为 1");
        assertEquals(0, completion.queries, "落终态那一拍不该再单独发中间事件（同一次查询只发一条）");
        verify(store, never()).scheduleNext(anyLong(), any(), any());
    }

    @Test
    @DisplayName("终态成功：产物转储一次，落库的是转储后的地址")
    void successArchivesOnce() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), isNull(), isNull())).thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://up/a.mp4\"}]}]}";
        archiveStore.result = "https://s3/a.mp4";

        assertTrue(poller.process(task));

        assertEquals(1, archiveStore.archived.size(), "终态只搬一次，不能在中间态尝试");
        ArchiveAsset asset = archiveStore.archived.get(0);
        assertEquals("tc-1", asset.taskCode(), "taskCode 是实现方做幂等键的唯一依据");
        assertEquals("up-1", asset.upstreamTaskId());
        assertEquals("https://up/a.mp4", asset.sourceUrl(), "转储吃的是供应商地址，不是我们自己的");
        assertEquals("kling-3", asset.modelCode());
        assertEquals("rule-1", asset.ruleCode());
        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.SUCCEEDED.code()),
                eq("{\"result_url\":\"https://s3/a.mp4\"}"), isNull(), isNull());
        assertEquals(1, completion.accounted);
    }

    @Test
    @DisplayName("⭐ 转储失败回退供应商地址：任务照样成功、照样计费")
    void archiveFailureFallsBackToUpstreamUrl() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), isNull(), isNull())).thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://up/a.mp4\"}]}]}";
        archiveStore.error = new IllegalStateException("s3 down");

        assertTrue(poller.process(task));

        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.SUCCEEDED.code()),
                eq("{\"result_url\":\"https://up/a.mp4\"}"), isNull(), isNull());
        assertEquals(1, completion.accounted, "转储是旁路，不能因为搬文件失败就不算钱");
        assertEquals(1, completion.published, "终态事件也不能少发——否则数仓看不到这笔成功");
    }

    @Test
    @DisplayName("转储返回空白按失败处理：不把空 URL 交给客户端")
    void blankArchivedUrlFallsBack() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), isNull(), isNull())).thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://up/a.mp4\"}]}]}";
        archiveStore.result = "   ";

        assertTrue(poller.process(task));

        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.SUCCEEDED.code()),
                eq("{\"result_url\":\"https://up/a.mp4\"}"), isNull(), isNull());
    }

    @Test
    @DisplayName("上游失败不转储：没有产物可搬")
    void failedTaskNeverArchives() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.FAILED.code()), any(), eq("UPSTREAM_TASK_FAILED"), any()))
                .thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"failed\"}],\"message\":\"prompt rejected\"}";

        assertTrue(poller.process(task));

        assertEquals(0, archiveStore.archived.size(), "没有产物时不该调用转储");
    }

    @Test
    @DisplayName("上游任务失败：按 FAILED 落终态，不计费")
    void upstreamFailureNeverBills() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.FAILED.code()), any(), eq("UPSTREAM_TASK_FAILED"), any()))
                .thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"failed\"}],\"message\":\"prompt rejected\"}";

        assertTrue(poller.process(task));

        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.FAILED.code()), any(), eq("UPSTREAM_TASK_FAILED"), any());
        assertEquals(0, completion.accounted, "上游失败不计费");
        assertEquals(1, completion.published, "失败也要发终态事件（观测链路不能断）");
    }

    @Test
    @DisplayName("成功但取不到产物：按失败落终态，不计费（钱收了没有货）")
    void successWithoutResultIsFailure() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.FAILED.code()), any(), eq("UPSTREAM_RESULT_MISSING"), any()))
                .thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\"}]}";

        assertTrue(poller.process(task));

        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.FAILED.code()), any(), eq("UPSTREAM_RESULT_MISSING"), any());
        assertEquals(0, completion.accounted, "没有产物绝不能计入金额");
    }

    @Test
    @DisplayName("非终态：安排下一次轮询，不落终态、不计费，但要发一条中间查询事件")
    void runningSchedulesNextOnly() {
        when(store.scheduleNext(anyLong(), any(), any())).thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"processing\"}]}";

        assertFalse(poller.process(task));

        verify(store).scheduleNext(eq(7L), eq(CLAIM_ID), any());
        verify(store, never()).complete(anyLong(), any(), anyInt(), any(), any(), any());
        assertEquals(0, completion.accounted);
        assertEquals(0, completion.published, "还没到终态，不能发终态事件");
        assertEquals(List.of(1), completion.querySeqs,
                "这次查询确实打到了上游，必须留一条 task_phase=query 的事件");
    }

    @Test
    @DisplayName("⭐ 占位已易主：写不进下一次轮询，就不报这一条查询事件")
    void lostClaimSkipsQueryEvent() {
        // mock 默认返回 false，正是「行已被别的节点回收」的形态
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"processing\"}]}";

        assertFalse(poller.process(task));

        assertEquals(1, queryClient.calls, "上游确实被调了一次（成本已发生，只是记不到这一行上）");
        assertEquals(0, completion.queries,
                "这一行的 query_seq 已归新主人报：两边都报会让数仓无从判断哪条才是真的");
    }

    @Test
    @DisplayName("⭐ 一次异步任务 = 1 次提交 + N 次查询：每查一次发一条，seq 单调递增，终态那条带 terminal")
    void eachQueryLeavesOneEvent() {
        AsyncTaskSpec spec = spec();
        when(store.scheduleNext(anyLong(), any(), any())).thenReturn(true);
        queryClient.body = "{\"data\":[{\"status\":\"processing\"}]}";

        poller.process(task(0, JsonKit.toJson(spec), JsonKit.toJson(context())));
        poller.process(task(1, JsonKit.toJson(spec), JsonKit.toJson(context())));

        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), isNull(), isNull())).thenReturn(true);
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://x/a.mp4\"}]}]}";
        poller.process(task(2, JsonKit.toJson(spec), JsonKit.toJson(context())));

        assertEquals(List.of(1, 2), completion.querySeqs,
                "两次非终态查询各留一条，且 seq 从 1 开始递增");
        assertEquals(1, completion.published);
        assertEquals(3, completion.terminalSeq, "第三次查询才拿到产物，终态事件必须自报 seq=3");
    }

    @Test
    @DisplayName("查询瞬时失败：退避后重试，不判终态，但中间事件要带上错误码")
    void queryErrorRetriesWithBackoff() {
        when(store.scheduleNext(anyLong(), any(), any())).thenReturn(true);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.error = new IllegalStateException("connection reset");

        assertFalse(poller.process(task));

        ArgumentCaptor<LocalDateTime> next = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(store).scheduleNext(eq(7L), eq(CLAIM_ID), next.capture());
        assertTrue(next.getValue().isAfter(LocalDateTime.now().minusSeconds(1)),
                "退避后的下一次轮询必须在未来，否则会变成忙等");
        verify(store, never()).complete(anyLong(), any(), anyInt(), any(), any(), any());
        assertEquals(List.of(1), completion.querySeqs,
                "失败那次也真实打了上游，不能因为没拿到响应就不记");
    }

    @Test
    @DisplayName("轮询次数用尽：转 EXPIRED，不再打上游")
    void attemptsExhaustedExpires() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.EXPIRED.code()), any(), eq("TASK_TIMEOUT"), any())).thenReturn(true);
        AsyncTaskSpec spec = spec();
        AsyncTaskEntity task = task(spec.maxAttempts(), JsonKit.toJson(spec), JsonKit.toJson(context()));

        assertTrue(poller.process(task));

        assertEquals(0, queryClient.calls, "次数用尽后不应再消耗上游查询额度");
        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.EXPIRED.code()), any(), eq("TASK_TIMEOUT"), any());
        verify(store, never()).scheduleNext(anyLong(), any(), any());
        assertEquals(0, completion.queries, "没打出这次查询，就不能凭空记一条");
        assertEquals(spec.maxAttempts(), completion.terminalSeq,
                "终态事件要如实报「一共查了几次」，不能因为最后一次没查就归零");
    }

    @Test
    @DisplayName("快照不可解析：按失败落终态，不静默留在在途态")
    void unreadableSnapshotFails() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.FAILED.code()), any(), eq("TASK_SNAPSHOT_INVALID"), any()))
                .thenReturn(true);
        AsyncTaskEntity task = task(0, "{oops", JsonKit.toJson(context()));

        assertTrue(poller.process(task));
        verify(store).complete(eq(7L), eq(CLAIM_ID), eq(AsyncTaskStatusEnum.FAILED.code()), any(), eq("TASK_SNAPSHOT_INVALID"), any());
    }

    @Test
    @DisplayName("终态 CAS 抢不到：不发事件（一条任务一条终态消息）")
    void alreadySettledSkipsEvent() {
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), any(), any())).thenReturn(false);
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://x/a.mp4\"}]}]}";

        assertTrue(poller.process(task));

        assertEquals(1, completion.accounted, "计费照做：金额是这一笔的账，与事件是否发出无关");
        assertEquals(0, completion.published, "已被别的节点落定的任务不得再发一条终态事件");
    }

    @Test
    @DisplayName("总开关关闭时一轮扫描什么都不做")
    void disabledTickDoesNothing() {
        properties.setEnabled(false);
        poller.tick();
        verify(store, never()).claim(any(), any(), anyInt());
        verify(store, never()).claimExpired(any(), any(), anyInt());
    }

    @Test
    @DisplayName("⭐ 超时回收先占位：用本轮的占位号落 EXPIRED，并补发一条终态事件")
    void expireTimedOutClaimsThenSettles() {
        AsyncTaskEntity expiredTask = task(2, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        expiredTask.setClaimId("claim-expired");
        when(store.claimExpired(any(), any(), anyInt())).thenReturn(List.of(expiredTask));
        when(store.complete(eq(7L), eq("claim-expired"), eq(AsyncTaskStatusEnum.EXPIRED.code()), any(), eq("TASK_TIMEOUT"), any()))
                .thenReturn(true);

        poller.tick();

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(store).claimExpired(any(), ttl.capture(), eq(properties.getExpireBatchSize()));
        assertEquals(Duration.ofMillis(properties.getClaimTtlMs()), ttl.getValue(),
                "超时回收同样要按「一次处理的预算」占位，不能把租约压成 0");
        verify(store).complete(eq(7L), eq("claim-expired"), eq(AsyncTaskStatusEnum.EXPIRED.code()), any(), eq("TASK_TIMEOUT"), any());
        assertEquals(0, completion.accounted, "超时不计费");
        assertEquals(1, completion.published,
                "超时也是一次状态转移，不补发终态事件的话这笔任务在数仓里就完全不存在");
    }

    @Test
    @DisplayName("没有超时任务时不落任何终态")
    void expireWithoutCandidatesWritesNothing() {
        when(store.claimExpired(any(), any(), anyInt())).thenReturn(List.of());

        poller.tick();

        verify(store, never()).complete(anyLong(), any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("⭐ 启动期校验预算：占位不长于查询超时就直接拒绝启动")
    void initRejectsClaimTtlShorterThanQueryTimeout() {
        properties.setClaimTtlMs(properties.getQueryTimeoutMs());

        IllegalStateException error = assertThrows(IllegalStateException.class, poller::init);
        assertTrue(error.getMessage().contains("claim-ttl-ms"),
                "报错要指名是哪个配置项——这个误配的后果「偶发少算上游调用数」事后查不出来");
    }

    @Test
    @DisplayName("启动期校验预算：查询超时非正数同样拒绝")
    void initRejectsNonPositiveQueryTimeout() {
        properties.setQueryTimeoutMs(0);

        assertThrows(IllegalStateException.class, poller::init);
    }

    @Test
    @DisplayName("扫描一轮：抢占到的任务逐个处理，空闲时零查询")
    void tickProcessesClaimed() {
        AsyncTaskEntity task = task(0, JsonKit.toJson(spec()), JsonKit.toJson(context()));
        when(store.claim(any(), any(), anyInt())).thenReturn(List.of(task));
        when(store.complete(anyLong(), any(), eq(AsyncTaskStatusEnum.SUCCEEDED.code()), any(), any(), any())).thenReturn(true);
        queryClient.body = "{\"data\":[{\"status\":\"succeed\",\"outputs\":[{\"url\":\"https://x/a.mp4\"}]}]}";

        poller.tick();

        verify(store).claim(any(), any(), eq(properties.getBatchSize()));
        assertEquals(1, queryClient.calls);
        assertEquals(1, completion.published);
    }

    @Test
    @DisplayName("归档清理按保留窗口删除，关闭开关时不跑")
    void archiveRemovesBeforeRetention() {
        properties.setRetentionDays(30);
        when(store.cleanup(any(), anyInt())).thenReturn(3);
        poller.archive();
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(store).cleanup(before.capture(), eq(properties.getCleanupBatchSize()));
        assertTrue(before.getValue().isBefore(LocalDateTime.now().minusDays(29)));
    }

    @Test
    @DisplayName("cron 拍点重叠：上一轮没跑完就丢掉这一拍，不叠加扫描")
    void tickSkipsOverlappingBeat() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(store.claim(any(), any(), anyInt())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        Thread worker = new Thread(poller::tick);
        worker.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "第一拍必须已经进到 claim");

        poller.tick();

        verify(store, times(1)).claim(any(), any(), anyInt());

        release.countDown();
        worker.join(5000);
        assertFalse(worker.isAlive());
    }

    @Test
    @DisplayName("cron 拍点重叠：归档上一轮没跑完也不叠加")
    void archiveSkipsOverlappingBeat() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(store.cleanup(any(), anyInt())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 0;
        });

        Thread worker = new Thread(poller::archive);
        worker.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        poller.archive();

        verify(store, times(1)).cleanup(any(), anyInt());

        release.countDown();
        worker.join(5000);
        assertFalse(worker.isAlive());
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
    private static AsyncTaskEntity task(int attempts, String specJson, String contextJson) {
        AsyncTaskEntity task = new AsyncTaskEntity();
        task.setId(7L);
        task.setTaskCode("tc-1");
        task.setProfileCode("KLING_TASK");
        task.setUpstreamTaskId("up-1");
        task.setStatus(AsyncTaskStatusEnum.PENDING.code());
        task.setAttempts(attempts);
        task.setClaimId(CLAIM_ID);
        task.setPollSpecJson(specJson);
        task.setContextJson(contextJson);
        task.setRequestJson("{\"prompt\":\"a cat\"}");
        return task;
    }

    /**
     * 转储桩：记录被搬过的素材，按测试意愿返回新地址 / 抛错 / 返回空白。
     *
     * <p>默认行为与生产默认实现（{@code passthrough}）一致——原样返回 {@code sourceUrl}，
     * 于是没显式设置 {@code result} 的用例都在验「没配转储时的行为」。</p>
     */
    private static final class RecordingArchiveStore implements ArchiveStore {

        private final java.util.List<ArchiveAsset> archived = new java.util.ArrayList<>();
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

    /** 只记录调用与返回固定响应体：查询客户端是具体类，用子类覆写而非 mock。 */
    private static final class RecordingQueryClient extends AsyncTaskQueryClient {

        private String body = "{}";
        private RuntimeException error;
        private int calls;

        private RecordingQueryClient() {
            super(null, null, null, null, null);
        }

        @Override
        public String query(AsyncTaskContext context, AsyncTaskSpec spec, String upstreamTaskId,
                            String traceparent) {
            calls++;
            if (error != null) {
                throw error;
            }
            return body;
        }
    }

    /** 同理由子类覆写：只数「计了几次费 / 发了几条终态事件 / 发了几条中间查询事件」。 */
    private static final class RecordingCompletion extends AsyncTaskCompletion {

        private int accounted;
        private int published;
        private int queries;
        private final java.util.List<Integer> querySeqs = new java.util.ArrayList<>();
        private int terminalSeq = -1;

        private RecordingCompletion() {
            super(null, null, null, null, "traffic");
        }

        @Override
        public UsageAccounting account(AsyncTaskEntity task, AsyncTaskContext context, String queryBody) {
            accounted++;
            return UsageAccounting.empty();
        }

        @Override
        public void publishQuery(AsyncTaskEntity task, AsyncTaskContext context, int querySeq, String errorCode) {
            queries++;
            querySeqs.add(querySeq);
        }

        @Override
        public void publishTerminal(AsyncTaskEntity task, AsyncTaskContext context,
                                    int querySeq, UsageAccounting usage, Throwable error) {
            published++;
            terminalSeq = querySeq;
        }
    }
}
