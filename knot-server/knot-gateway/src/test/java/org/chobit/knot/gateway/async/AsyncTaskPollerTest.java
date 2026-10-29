package org.chobit.knot.gateway.async;

import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：轮询器的<b>节拍</b>（{@link AsyncTaskScheduledPoller}）。
 *
 * <p>⭐ 2026-10-29 拆分：原来这个类同时测「怎么扫」和「扫到之后做什么」。编排抽出成
 * {@code AsyncTaskRunner} 之后，业务断言整体搬到 {@code AsyncTaskRunnerTest}，
 * 这里只留<b>部署语义</b>——开关、启动期预算校验、拍点重叠时跳过而不是排队、并发扇出。
 * 一条都没删：拆的是归属，不是覆盖率。</p>
 *
 * <p>⚠ 编排层用<b>子类覆写</b>而不是 mock：{@code AsyncTaskRunner} 是具体类，
 * 且在 JDK 25 + 当前 Mockito 组合下 mock 具体类不可靠；子类覆写既稳定又直白
 * （它与 {@code RecordingQueryClient} 等既有桩同一种做法）。</p>
 */
class AsyncTaskPollerTest {

    private AsyncTaskProperties properties;
    private RecordingService service;
    private AsyncTaskScheduledPoller poller;

    @BeforeEach
    void setUp() {
        properties = new AsyncTaskProperties();
        properties.setNodeId("node-test");
        service = new RecordingService();
        poller = new AsyncTaskScheduledPoller(properties, service, null, new RecordingDelivery());
        poller.init();
    }

    @Test
    @DisplayName("总开关关闭时一轮扫描什么都不做")
    void disabledTickDoesNothing() {
        properties.setEnabled(false);

        poller.tick();

        assertEquals(0, service.expiredCalls, "关掉开关就不该去抢超时任务的租约");
        assertEquals(0, service.claimCalls, "关掉开关就不该扫表");
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
    @DisplayName("一轮扫描：先回收超时、再抢占，抢到的逐个交给编排层处理")
    void tickProcessesClaimed() {
        service.due = List.of(task());

        poller.tick();

        assertEquals(1, service.expiredCalls, "每拍都要先回收超时任务，不能只扫待办");
        assertEquals(1, service.claimCalls);
        assertEquals(1, service.processed.size(), "抢到几条就处理几条");
        assertEquals("tc-1", service.processed.get(0).taskCode());
    }

    @Test
    @DisplayName("归档节拍只调清理入口（保留窗口的口径在编排层，不在这里）")
    void archiveCallsCleanup() {
        poller.archive();

        assertEquals(1, service.cleanupCalls);
    }

    @Test
    @DisplayName("cron 拍点重叠：上一轮没跑完就丢掉这一拍，不叠加扫描")
    void tickSkipsOverlappingBeat() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service.enterClaim = entered;
        service.holdClaim = release;

        Thread worker = new Thread(poller::tick);
        worker.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "第一拍必须已经进到抢占");

        poller.tick();

        assertEquals(1, service.claimCalls, "第二拍应被守卫直接丢掉，而不是排队");

        release.countDown();
        worker.join(5000);
        assertFalse(worker.isAlive());
    }

    @Test
    @DisplayName("cron 拍点重叠：归档上一轮没跑完也不叠加")
    void archiveSkipsOverlappingBeat() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service.enterCleanup = entered;
        service.holdCleanup = release;

        Thread worker = new Thread(poller::archive);
        worker.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        poller.archive();

        assertEquals(1, service.cleanupCalls);

        release.countDown();
        worker.join(5000);
        assertFalse(worker.isAlive());
    }

    private static AsyncTaskDto task() {
        return new AsyncTaskDto(7L, "tc-1", "KLING_TASK", null, "up-1",
                AsyncTaskStatusEnum.PENDING.code(), 0, 0, null, "claim-1",
                "{}", "{}", "{}",
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 编排层桩：只记「被叫了几次 / 交给我几条」，不做任何业务判断。
     *
     * <p>⚠ 两个 latch 用于「拍点重叠」用例：把 {@code claimDue} / {@code cleanup} 卡住，
     * 才能造出「上一拍还没跑完」的时序。放进桩而不是用 sleep，是避免用例变成靠时序碰运气。</p>
     */
    private static final class RecordingService extends AsyncTaskRunner {

        private static final AsyncTaskRunPolicy POLICY = new AsyncTaskRunPolicy(100, 200, 180_000L, 30, 500);

        private List<AsyncTaskDto> due = List.of();
        private int expiredCalls;
        private int claimCalls;
        private int cleanupCalls;
        private final List<AsyncTaskDto> processed = new ArrayList<>();
        private CountDownLatch enterClaim;
        private CountDownLatch holdClaim;
        private CountDownLatch enterCleanup;
        private CountDownLatch holdCleanup;

        private RecordingService() {
            super(null, null, null, null, POLICY);
        }

        @Override
        public void expireTimedOut(String claimId) {
            expiredCalls++;
        }

        @Override
        public List<AsyncTaskDto> claimDue(String claimId) {
            claimCalls++;
            awaitIfNeeded(enterClaim, holdClaim);
            return due;
        }

        @Override
        public boolean process(AsyncTaskDto task) {
            processed.add(task);
            return true;
        }

        @Override
        public int cleanup() {
            cleanupCalls++;
            awaitIfNeeded(enterCleanup, holdCleanup);
            return 0;
        }

        private static void awaitIfNeeded(CountDownLatch entered, CountDownLatch release) {
            if (entered == null || release == null) {
                return;
            }
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 交付编排桩：本类只测「轮询节拍」，「交付节拍」不在这条链路上。
     *
     * <p>⚠ 保留它是因为 {@link AsyncTaskScheduledPoller} 现在同时持有两条编排——
     * 传 {@code null} 会在 {@code deliver()} 被意外触发时 NPE，掩盖真正的断言失败。</p>
     */
    private static final class RecordingDelivery extends AsyncTaskDeliveryRunner {

        private static final AsyncTaskDeliveryPolicy POLICY =
                new AsyncTaskDeliveryPolicy(100, 60_000L, 3, 5_000, 300_000);

        private RecordingDelivery() {
            super(null, null, POLICY);
        }

        @Override
        public List<AsyncTaskDto> claimDue(String claimId) {
            return List.of();
        }
    }
}
