package org.chobit.knot.gateway.async;

import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.async.AsyncTaskSupport.AsyncTaskCreation;
import org.chobit.knot.gateway.model.ProxyResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：异步任务的映射与判定（{@link AsyncTaskSupport}）。
 *
 * <p>本类是唯一的厂商知识边界，判定错了的表现是「永远查不到」或「提前判死」，
 * 两类都只在生产环境、几分钟后才显现，故逐条钉死。</p>
 */
class AsyncTaskSupportTest {

    private final AsyncTaskSupport support = new AsyncTaskSupport(null, null, null);

    private static AsyncTaskSpec spec() {
        return new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.id", null, "data[0].status",
                "succeed", "failed", null, "data[0].outputs[0].url",
                7, 5000, 1000, 30000, 360, 1_800_000L);
    }

    @Test
    @DisplayName("异步与否只看绑定上的档案编码：为空/空白一律同步")
    void asyncIsBindingProperty() {
        ModelApiBindingEntity binding = new ModelApiBindingEntity();
        assertNull(support.profileCodeOf(binding));
        assertNull(support.profileCodeOf(null));
        binding.setAsyncProfileCode("  ");
        assertNull(support.profileCodeOf(binding), "空白档案编码等同未配置");
        binding.setAsyncProfileCode(" KLING_TASK ");
        assertEquals("KLING_TASK", support.profileCodeOf(binding));
    }

    @Test
    @DisplayName("上游任务 ID 按 task_id_path 取，不是按 query_path")
    void upstreamTaskIdUsesTaskIdPath() {
        Map<String, Object> createBody = Map.of("data", Map.of("id", "up-1",
                "status", "submitted"));
        assertEquals("up-1", support.upstreamTaskIdOf(createBody, spec()));
        // 探针：若误用 query_path（含占位）去取 ID，这里就会取到空——两者是不同路径
        assertNull(support.upstreamTaskIdOf(createBody,
                new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.task_id", null, "data.status",
                        "succeed", "failed", null, "data.url", null, 5000, 1000, 30000, 360, 1_800_000L)),
                "task_id_path 填错必须取不到，不得回落到 query_path");
    }

    @Test
    @DisplayName("终态判定：成功 / 失败 / 未声明状态一律非终态 / 取不到状态不判死")
    void terminalClassification() {
        AsyncTaskSpec spec = spec();
        Map<String, Object> running = Map.of("data", java.util.List.of(Map.of("status", "processing")));
        Map<String, Object> done = Map.of("data", java.util.List.of(Map.of("status", "succeed",
                "outputs", java.util.List.of(Map.of("url", "https://x/a.mp4")))));
        Map<String, Object> failed = Map.of("data", java.util.List.of(Map.of("status", "failed")));
        Map<String, Object> empty = Map.of("data", java.util.List.of(Map.of()));
        assertSame(AsyncTaskSupport.Terminal.RUNNING, support.terminalOf(running, spec));
        assertSame(AsyncTaskSupport.Terminal.SUCCEEDED, support.terminalOf(done, spec));
        assertSame(AsyncTaskSupport.Terminal.FAILED, support.terminalOf(failed, spec));
        assertSame(AsyncTaskSupport.Terminal.RUNNING, support.terminalOf(empty, spec),
                "取不到状态时按进行中处理：上游抖动不该把任务判死（由 max_attempts / 超时兜底）");
    }

    @Test
    @DisplayName("产物 URL 取不到返回 null，由调用方判为「成功但无货」")
    void resultUrlMissingYieldsNull() {
        Map<String, Object> body = Map.of("data", java.util.List.of(Map.of("status", "succeed")));
        assertNull(support.resultUrlOf(body, spec()));
        Map<String, Object> ok = Map.of("data", java.util.List.of(Map.of("status", "succeed",
                "outputs", java.util.List.of(Map.of("url", "https://x/a.mp4")))));
        assertEquals("https://x/a.mp4", support.resultUrlOf(ok, spec()));
    }

    @Test
    @DisplayName("退避：指数增长、受上限封顶、且不低于基准间隔")
    void backoffIsCappedAndFloored() {
        AsyncTaskSpec spec = spec();
        assertEquals(5000, support.nextDelayMs(1, spec), "首次退避不应低于基准轮询间隔");
        assertEquals(5000, support.nextDelayMs(2, spec));
        assertEquals(8000, support.nextDelayMs(4, spec));
        assertEquals(30000, support.nextDelayMs(20, spec), "退避必须封顶，否则越查越慢直到超时");
        // 探针：若忘记取 min(cap, ...)，第 20 次会算成 1000×2^19 ≈ 5.2e8 ms
        assertTrue(support.nextDelayMs(20, spec) <= 30000);
    }

    @Test
    @DisplayName("任务行组装：在途态、0 次、三个 JSON 段齐全、产物时效按天")
    void buildsPendingRow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 12, 0, 0);
        AsyncTaskCreation creation = new AsyncTaskCreation(
                "tc-1", "KLING_TASK", spec(), null,
                Map.of("prompt", "a cat"),
                new AsyncTaskContext("tc-1", "rule-1", null, null, "req-1",
                        "VIDEO_GENERATIONS", "kling-3", "kling-3-turbo", "kling", "pool-1", "https://x/videos"),
                "up-1", null, "RULE-1", "V1", now);
        AsyncTaskEntity task = support.build(creation);

        assertEquals("tc-1", task.getTaskCode());
        assertEquals("KLING_TASK", task.getProfileCode());
        assertEquals("up-1", task.getUpstreamTaskId());
        assertEquals("PENDING", task.getStatus());
        assertEquals(0, task.getAttempts());
        assertEquals(now.plusNanos(5_000_000_000L), task.getNextPollAt());
        assertEquals(now.plusNanos(1_800_000_000_000L), task.getExpiresAt());
        assertEquals(now.plusDays(7), task.getResultExpiresAt());
        assertEquals("RULE-1", task.getBillingRuleCode());
        assertEquals("V1", task.getBillingRuleVersionCode());
        assertNotNull(task.getRequestJson());
        assertNotNull(task.getPollSpecJson());
        assertNotNull(task.getContextJson());
        // 探针：快照必须能反序列化回同样的口径，否则「改档案不影响在途任务」是空话
        assertEquals(spec(), JsonKit.fromJson(task.getPollSpecJson(), AsyncTaskSpec.class));
    }

    @Test
    @DisplayName("产物时效未配置时不写 result_expires_at")
    void noResultTtl() {
        AsyncTaskSpec noTtl = new AsyncTaskSpec("GET", "/t/{task_id}", "data.id", null, "data.status",
                "succeed", "failed", null, "data.url", null, 5000, 1000, 30000, 360, 1_800_000L);
        AsyncTaskEntity task = support.build(new AsyncTaskCreation("tc", "P", noTtl, null,
                Map.of(), new AsyncTaskContext("tc", "r", null, null, "req", null, "m", null, null, null, null),
                "up", null, null, null, LocalDateTime.now()));
        assertNull(task.getResultExpiresAt());
    }

    @Test
    @DisplayName("非异步或不完整的上游响应一律回退同步，不落任务")
    void submitFallsBackToSync() {
        assertNull(support.submitIfAsync(null, null, null), "无上游结果时不判定");
        assertNull(support.submitIfAsync(null, null, new ProxyResult("  ", 1L, 1L, null)),
                "空响应体不是一次成功的任务创建");
    }

    @Test
    @DisplayName("任务句柄：32 位小写 hex，不暴露上游 ID")
    void taskCodeShape() {
        String code = AsyncTaskSupport.newTaskCode();
        assertEquals(32, code.length());
        assertTrue(code.matches("[0-9a-f]{32}"), code);
        assertFalse(code.contains("-"));
    }

    @Test
    @DisplayName("终态取值集合解析供日志与校验使用")
    void splitsCandidates() {
        assertEquals(java.util.List.of("a", "b"), AsyncTaskSupport.splitValues("a,b"));
        assertTrue(AsyncTaskSupport.splitValues(" ").isEmpty());
    }
}
