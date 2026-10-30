package org.chobit.knot.gateway.async;

import org.chobit.knot.common.constants.enums.AsyncTaskDeliveryStatusEnum;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：回调交付的编排（{@link AsyncTaskDeliveryRunner}）。
 *
 * <p>守四条，任一条失守都会变成对外可见的缺陷：</p>
 * <ol>
 *   <li><b>成功只落交付维度</b>：{@code delivery_status=成功}，<b>不改</b> {@code task_status}；</li>
 *   <li><b>失败退避而非立刻放弃</b>：未到上限写 {@code reschedule}，到上限才落 {@code FAILED}；</li>
 *   <li><b>放弃不等于任务失败</b>：交付 FAILED 只是「没送到」，任务仍是原来的终态；</li>
 *   <li><b>租约易主不谎报</b>：写回失败时返回 false，由新主人续投（可能重复一次，客户端按 task_id 幂等）。</li>
 * </ol>
 *
 * <p>⚠ 编排层用<b>子类覆写</b>而不是 Mockito：{@code AsyncTaskService} 是具体类，JDK 25 下
 * Mockito 无法 instrument（同 {@code AsyncTaskRunnerTest} 的做法）。</p>
 */
class AsyncTaskDeliveryRunnerTest {

    private static final String CLAIM_ID = "deliver-1";

    private static final AsyncTaskDeliveryPolicy POLICY =
            new AsyncTaskDeliveryPolicy(100, 60_000L, 3, 5_000, 300_000);

    private RecordingTaskService taskService;
    private RecordingCallback callback;
    private AsyncTaskDeliveryRunner runner;

    @BeforeEach
    void setUp() {
        taskService = new RecordingTaskService();
        callback = new RecordingCallback();
        runner = new AsyncTaskDeliveryRunner(taskService, callback, POLICY);
    }

    @Test
    @DisplayName("成功：投递一次，落「回调成功」，带上尝试次数与送达时刻")
    void successMarksDelivered() {
        taskService.completeResult = true;

        assertTrue(runner.deliver(task("https://client.example/cb", 0), CLAIM_ID));

        assertEquals(1, callback.urls.size(), "成功只投一次");
        AsyncTaskQueryResponse payload = callback.payloads.get(0);
        assertEquals("tc-1", payload.taskCode(), "客户端按 task_id 幂等，必须带对");
        assertEquals("completed", payload.status(), "回调 body 与轮询响应同形");
        assertEquals("https://x/a.mp4", payload.result().get("result_url"));

        CompleteDeliveryCall done = taskService.onlyComplete();
        assertEquals(AsyncTaskDeliveryStatusEnum.SUCCEEDED.code(), done.status());
        assertEquals(1, done.attempts());
        assertNotNull(done.deliveredAt());
        assertNull(done.error());
        assertEquals(0, taskService.reschedules.size(), "成功了不该再排重试");
    }

    @Test
    @DisplayName("失败未到上限：退避后重试，不落放弃")
    void failureReschedulesWithBackoff() {
        callback.error = new IllegalStateException("boom");

        assertFalse(runner.deliver(task("https://client.example/cb", 0), CLAIM_ID));

        RescheduleCall retry = taskService.onlyReschedule();
        assertEquals(1, retry.attempts(), "本次是第 1 次尝试");
        assertTrue(retry.nextDeliveryAt().isAfter(LocalDateTime.now().minusSeconds(1)),
                "下一次投递必须在未来，否则变成忙等");
        assertEquals("boom", retry.error());
        assertEquals(0, taskService.completes.size(), "未到上限不许落放弃");
    }

    @Test
    @DisplayName("⭐ 到达上限：落「回调失败」，但只动交付维度，任务仍是 completed")
    void givesUpAtLimit() {
        callback.error = new IllegalStateException("boom");
        // 已失败 2 次 + 本次 = 3，达到上限 3
        assertFalse(runner.deliver(task("https://client.example/cb", 2), CLAIM_ID));

        CompleteDeliveryCall givenUp = taskService.onlyComplete();
        assertEquals(AsyncTaskDeliveryStatusEnum.FAILED.code(), givenUp.status());
        assertEquals(3, givenUp.attempts());
        assertNull(givenUp.deliveredAt(), "放弃不是送达，不能写送达时刻");
        assertEquals("boom", givenUp.error());
        assertEquals(0, taskService.reschedules.size());
    }

    @Test
    @DisplayName("没有 callback_url 的行被误扫到：收口成「无需回调」，不空转")
    void blankUrlStopsDelivery() {
        assertFalse(runner.deliver(task(null, 0), CLAIM_ID));

        assertEquals(0, callback.urls.size(), "没有地址就不该发请求");
        CompleteDeliveryCall stopped = taskService.onlyComplete();
        assertEquals(AsyncTaskDeliveryStatusEnum.NONE.code(), stopped.status());
    }

    @Test
    @DisplayName("⭐ 租约易主：投递已发出但写回失败，如实返回 false（不谎报成功）")
    void claimLostDoesNotLie() {
        taskService.completeResult = false;

        assertFalse(runner.deliver(task("https://client.example/cb", 0), CLAIM_ID));

        assertEquals(1, callback.urls.size(), "请求确实发出去了");
        assertEquals(1, taskService.completes.size(),
                "写回尝试过（被 CAS 挡掉），由新主人按未送达重试");
    }

    @Test
    @DisplayName("退避：base × 2^(n-1)，封顶")
    void backoffDoublesAndCaps() {
        assertEquals(5_000L, runner.nextDelayMs(1));
        assertEquals(10_000L, runner.nextDelayMs(2));
        assertEquals(20_000L, runner.nextDelayMs(3));
        assertEquals(300_000L, runner.nextDelayMs(20), "不能无限翻倍");
    }

    // ───────────────────────────── 桩 ─────────────────────────────

    private static AsyncTaskDto task(String callbackUrl, int deliveryAttempts) {
        LocalDateTime completed = LocalDateTime.of(2026, 10, 29, 12, 1);
        return new AsyncTaskDto(7L, "tc-1", "KLING_TASK", null, "up-1",
                AsyncTaskStatusEnum.SUCCEEDED.code(), 1, 0, null, null,
                "{\"prompt\":\"a cat\"}", "{}", "{}", "{\"result_url\":\"https://x/a.mp4\"}",
                null, null, null, null, null, null,
                LocalDateTime.of(2026, 10, 29, 12, 0), completed,
                LocalDateTime.of(2026, 10, 29, 12, 30), LocalDateTime.of(2026, 11, 5, 12, 0),
                callbackUrl, AsyncTaskDeliveryStatusEnum.PENDING.code(), deliveryAttempts,
                completed, null, null);
    }

    private record CompleteDeliveryCall(Long id, String claimId, int status, int attempts,
                                        LocalDateTime deliveredAt, String error) {
    }

    private record RescheduleCall(Long id, String claimId, int attempts,
                                  LocalDateTime nextDeliveryAt, String error) {
    }

    private static final class RecordingTaskService extends AsyncTaskService {

        private final List<CompleteDeliveryCall> completes = new ArrayList<>();
        private final List<RescheduleCall> reschedules = new ArrayList<>();
        private boolean completeResult = true;

        private RecordingTaskService() {
            // 覆写掉的方法都不碰 Mapper，传 null 安全
            super(null);
        }

        @Override
        public List<AsyncTaskDto> claimDeliveries(String claimId, Duration claimTtl, int limit) {
            return List.of();
        }

        @Override
        public boolean completeDelivery(Long id, String claimId, int status, int attempts,
                                        LocalDateTime deliveredAt, String error) {
            completes.add(new CompleteDeliveryCall(id, claimId, status, attempts, deliveredAt, error));
            return completeResult;
        }

        @Override
        public boolean rescheduleDelivery(Long id, String claimId, int attempts,
                                          LocalDateTime nextDeliveryAt, String error) {
            reschedules.add(new RescheduleCall(id, claimId, attempts, nextDeliveryAt, error));
            return true;
        }

        private CompleteDeliveryCall onlyComplete() {
            assertEquals(1, completes.size(), "期望落一次交付结果");
            return completes.get(0);
        }

        private RescheduleCall onlyReschedule() {
            assertEquals(1, reschedules.size(), "期望只排一次重试");
            return reschedules.get(0);
        }
    }

    private static final class RecordingCallback implements AsyncTaskCallbackPort {

        private final List<String> urls = new ArrayList<>();
        private final List<AsyncTaskQueryResponse> payloads = new ArrayList<>();
        private RuntimeException error;

        @Override
        public void deliver(String callbackUrl, AsyncTaskQueryResponse payload) {
            urls.add(callbackUrl);
            payloads.add(payload);
            if (error != null) {
                throw error;
            }
        }
    }
}
