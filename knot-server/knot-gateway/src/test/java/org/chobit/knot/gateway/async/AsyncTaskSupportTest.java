package org.chobit.knot.gateway.async;

import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.async.AsyncTaskSupport.AsyncTaskCreation;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：异步任务的<b>提交侧</b>（{@link AsyncTaskSupport}）。
 *
 * <p>⭐ 判定、产物取值、退避三组用例已随代码搬到 {@code AsyncTaskSpecRulesTest}
 * （2026-10-29）。这里只剩「提交那一刻」才成立的事实：异步与否看绑定、任务行怎么组装、
 * 非异步时如何回退同步。回退错了的表现是「同步调用被当成异步挂住」，客户端一直拿不到结果。</p>
 */
class AsyncTaskSupportTest {

    private final AsyncTaskSupport support = new AsyncTaskSupport(null, null, null);

    private static AsyncTaskSpec spec() {
        return new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.id", "data[0].status",
                "['succeed','SUCCEEDED'] contains status", "status == 'failed'", null,
                "data[0].outputs[0].url",
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
    @DisplayName("任务行组装：三个 JSON 段齐全、产物时效按天、下一次轮询与寿命按档案")
    void buildsPendingRow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 12, 0, 0);
        AsyncTaskCreation creation = new AsyncTaskCreation(
                "tc-1", "KLING_TASK", spec(), null,
                Map.of("prompt", "a cat"),
                new AsyncTaskContext("tc-1", "rule-1", null, null, "req-1",
                        "VIDEO_GENERATIONS", "kling-3", "kling-3-turbo", "kling", "pool-1", "https://x/videos"),
                "up-1", "RULE-1", "V1", now);
        AsyncTaskDraftDto task = support.build(creation);

        assertEquals("tc-1", task.taskCode());
        assertEquals("KLING_TASK", task.profileCode());
        assertEquals("up-1", task.upstreamTaskId());
        // ⭐ 「新任务必定是 PENDING、查询次数必定是 0」这两条不在这里断言了：
        //    初值的赋予权随数据访问一起搬到了 svc 的 AsyncTaskConverter，
        //    钉 wire 值的断言也随之搬进 AsyncTaskConverterTest（能力搬家，测试必须跟着搬，
        //    否则删了能力测试还绿，等于没测）。草稿里根本没有这两个字段。
        assertEquals(now.plusNanos(5_000_000_000L), task.nextPollAt());
        assertEquals(now.plusNanos(1_800_000_000_000L), task.expiresAt());
        assertEquals(now.plusDays(7), task.resultExpiresAt());
        assertEquals("RULE-1", task.billingRuleCode());
        assertEquals("V1", task.billingRuleVersionCode());
        assertNotNull(task.requestJson());
        assertNotNull(task.pollSpecJson());
        assertNotNull(task.contextJson());
        // 探针：快照必须能反序列化回同样的口径，否则「改档案不影响在途任务」是空话
        assertEquals(spec(), JsonKit.fromJson(task.pollSpecJson(), AsyncTaskSpec.class));
    }

    @Test
    @DisplayName("产物时效未配置时不写 result_expires_at")
    void noResultTtl() {
        AsyncTaskSpec noTtl = new AsyncTaskSpec("GET", "/t/{task_id}", "data.id", "data.status",
                "status == 'succeed'", "status == 'failed'", null, "data.url",
                null, 5000, 1000, 30000, 360, 1_800_000L);
        AsyncTaskDraftDto task = support.build(new AsyncTaskCreation("tc", "P", noTtl, null,
                Map.of(), new AsyncTaskContext("tc", "r", null, null, "req", null, "m", null, null, null, null),
                "up", null, null, LocalDateTime.now()));
        assertNull(task.resultExpiresAt());
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
}
