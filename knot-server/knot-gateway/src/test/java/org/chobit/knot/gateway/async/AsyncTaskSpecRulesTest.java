package org.chobit.knot.gateway.async;

import org.chobit.knot.svc.service.support.AsyncTaskTerminalSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：档案求值规则（{@link AsyncTaskSpecRules}）。
 *
 * <p>⭐ <b>本类由 {@code AsyncTaskSupportTest} 拆出</b>（2026-10-29 规则单独成类）：
 * 判定、产物取值、退避这三组用例跟着代码一起搬，一条都没删。判定错了的表现是
 * 「永远查不到」或「提前判死」，两类都只在生产环境、几分钟后才显现，故逐条钉死。</p>
 *
 * <p>⚠ 这些用例<b>不需要</b>任何 Spring 容器、实体或缓存：规则是纯函数，
 * 输入是响应 {@code Map} 与 {@code AsyncTaskSpec}。这正是它可以被穷举的前提——
 * 换成任何能访问缓存/DB 的形态，就得起上下文才跑得起来，用例数会立刻缩水。</p>
 */
class AsyncTaskSpecRulesTest {

    private static AsyncTaskSpec spec() {
        return new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.id", "data[0].status",
                "['succeed','SUCCEEDED'] contains status", "status == 'failed'", null,
                "data[0].outputs[0].url", null, null,
                7, 5000, 1000, 30000, 360, 1_800_000L);
    }

    @Test
    @DisplayName("上游任务 ID 按 task_id_path 取，不是按 query_path")
    void upstreamTaskIdUsesTaskIdPath() {
        Map<String, Object> createBody = Map.of("data", Map.of("id", "up-1",
                "status", "submitted"));
        assertEquals("up-1", AsyncTaskSpecRules.upstreamTaskIdOf(createBody, spec()));
        // 探针：若误用 query_path（含占位）去取 ID，这里就会取到空——两者是不同路径
        assertNull(AsyncTaskSpecRules.upstreamTaskIdOf(createBody,
                new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.task_id", "data.status",
                        "status == 'succeed'", "status == 'failed'", null, "data.url", null, null,
                        null, 5000, 1000, 30000, 360, 1_800_000L)),
                "task_id_path 填错必须取不到，不得回落到 query_path");
    }

    @Test
    @DisplayName("终态判定：成功 / 失败 / 未声明状态一律非终态 / 取不到状态不判死")
    void terminalClassification() {
        AsyncTaskSpec spec = spec();
        Map<String, Object> running = Map.of("data", List.of(Map.of("status", "processing")));
        Map<String, Object> done = Map.of("data", List.of(Map.of("status", "succeed",
                "outputs", List.of(Map.of("url", "https://x/a.mp4")))));
        Map<String, Object> failed = Map.of("data", List.of(Map.of("status", "failed")));
        Map<String, Object> empty = Map.of("data", List.of(Map.of()));
        assertSame(AsyncTaskTerminalSupport.Terminal.RUNNING, AsyncTaskSpecRules.terminalOf(running, spec));
        assertSame(AsyncTaskTerminalSupport.Terminal.SUCCEEDED, AsyncTaskSpecRules.terminalOf(done, spec));
        assertSame(AsyncTaskTerminalSupport.Terminal.FAILED, AsyncTaskSpecRules.terminalOf(failed, spec));
        assertSame(AsyncTaskTerminalSupport.Terminal.RUNNING, AsyncTaskSpecRules.terminalOf(empty, spec),
                "取不到状态时按进行中处理：上游抖动不该把任务判死（由 max_attempts / 超时兜底）");
    }

    @Test
    @DisplayName("判定表达式多值用 contains（MVEL 没有 in 元组语法），大小写由表达式自己负责")
    void succeededExpressionIsCaseSensitive() {
        AsyncTaskSpec spec = spec();
        assertSame(AsyncTaskTerminalSupport.Terminal.SUCCEEDED,
                AsyncTaskSpecRules.terminalOf(Map.of("data", List.of(Map.of("status", "SUCCEEDED"))), spec));
        assertSame(AsyncTaskTerminalSupport.Terminal.RUNNING,
                AsyncTaskSpecRules.terminalOf(Map.of("data", List.of(Map.of("status", "Succeed"))), spec),
                "旧实现自动忽略大小写与空白；现在由表达式决定，未声明的写法不判成功");
    }

    @Test
    @DisplayName("判定表达式可读整个响应体：状态在 data[0].status，成功标志在别处也能配")
    void testExpressionCanReadWholeBody() {
        AsyncTaskSpec spec = new AsyncTaskSpec("GET", "/t/{task_id}", "data.id", "data[0].status",
                "body.data[0].code == 0", "status == 'failed'", null, "data[0].url", null, null,
                null, 5000, 1000, 30000, 360, 1_800_000L);
        assertSame(AsyncTaskTerminalSupport.Terminal.SUCCEEDED,
                AsyncTaskSpecRules.terminalOf(Map.of("data", List.of(Map.of("status", "ok", "code", 0))), spec));
    }

    @Test
    @DisplayName("配了 status_running 却谁都不匹配：仍回 RUNNING（不判死），但必须留 warn 信号")
    void unmatchedStatusStaysRunning() {
        AsyncTaskSpec spec = new AsyncTaskSpec("GET", "/t/{task_id}", "data.id", "data.status",
                "status == 'succeed'", "status == 'failed'", "status == 'queued'", "data.url", null, null,
                null, 5000, 1000, 30000, 360, 1_800_000L);
        assertSame(AsyncTaskTerminalSupport.Terminal.RUNNING,
                AsyncTaskSpecRules.terminalOf(Map.of("data", Map.of("status", "brand-new-state")), spec),
                "上游新增状态不应被当终态，也不应被当成「正常进行中」而静默吞掉");
    }

    @Test
    @DisplayName("产物 URL 取不到返回 null，由调用方判为「成功但无货」")
    void resultUrlMissingYieldsNull() {
        Map<String, Object> body = Map.of("data", List.of(Map.of("status", "succeed")));
        assertNull(AsyncTaskSpecRules.resultUrlOf(body, spec()));
        Map<String, Object> ok = Map.of("data", List.of(Map.of("status", "succeed",
                "outputs", List.of(Map.of("url", "https://x/a.mp4")))));
        assertEquals("https://x/a.mp4", AsyncTaskSpecRules.resultUrlOf(ok, spec()));
    }

    @Test
    @DisplayName("退避：指数增长、受上限封顶、且不低于基准间隔")
    void backoffIsCappedAndFloored() {
        AsyncTaskSpec spec = spec();
        assertEquals(5000, AsyncTaskSpecRules.nextDelayMs(1, spec), "首次退避不应低于基准轮询间隔");
        assertEquals(5000, AsyncTaskSpecRules.nextDelayMs(2, spec));
        assertEquals(8000, AsyncTaskSpecRules.nextDelayMs(4, spec));
        assertEquals(30000, AsyncTaskSpecRules.nextDelayMs(20, spec), "退避必须封顶，否则越查越慢直到超时");
        // 探针：若忘记取 min(cap, ...)，第 20 次会算成 1000×2^19 ≈ 5.2e8 ms
        assertTrue(AsyncTaskSpecRules.nextDelayMs(20, spec) <= 30000);
    }
}
