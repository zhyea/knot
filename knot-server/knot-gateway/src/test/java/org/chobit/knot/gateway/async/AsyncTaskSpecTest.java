package org.chobit.knot.gateway.async;

import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：查询口径快照（{@link AsyncTaskSpec}）。
 *
 * <p>快照是「同一笔任务无论何时完成都能复现」的载体：占位替换一旦漂移，
 * 表现是任务查不到，且金额无法复现。</p>
 *
 * <p><b>这里只管「数据怎么带过去」，不管「表达式怎么算」</b>：{@code *Path} 与
 * {@code status*} 的求值语义在 {@link AsyncTaskExpressionSupportTest} 与
 * {@link AsyncTaskSupportTest} 里守。判定语义留在快照上曾经导致三处各写一份、必然漂移。</p>
 */
class AsyncTaskSpecTest {

    private static AsyncTaskSpec spec(String queryPath) {
        return new AsyncTaskSpec("GET", queryPath, "data.id", "data[0].status",
                "['succeed','SUCCEEDED'] contains status", "status == 'failed'", null,
                "data[0].outputs[0].url",
                7, 5000, 1000, 30000, 360, 1_800_000L);
    }

    @Test
    @DisplayName("占位替换：{task_id}")
    void resolvesPlaceholders() {
        assertEquals("/v1/videos/generations/t-1",
                spec("/v1/videos/generations/{task_id}").resolvePath("t-1"));
    }

    @Test
    @DisplayName("⭐ {external_task_id} 已废除：档案里写它会在拼路径时直接抛，而不是静默查不到")
    void externalTaskIdPlaceholderIsGone() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> spec("/v1/tasks/{external_task_id}?by=external").resolvePath("t-1"));
        assertTrue(error.getMessage().contains("unresolved placeholder"), error.getMessage());
    }

    @Test
    @DisplayName("占位没替换完直接抛：拼出来的是查不到东西的 URL，比明确失败难排查")
    void unresolvedPlaceholderThrows() {
        AsyncTaskSpec spec = spec("/v1/videos/{unknown}/status");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> spec.resolvePath("t-1"));
        assertTrue(error.getMessage().contains("unresolved placeholder"), error.getMessage());
    }

    @Test
    @DisplayName("查询路径未配置时抛，不静默拼出根路径")
    void missingQueryPathThrows() {
        assertThrows(IllegalStateException.class, () -> spec(null).resolvePath("t-1"));
    }

    @Test
    @DisplayName("档案投影：表达式原样带过去（不在这里解析），空值走默认，非法值（0/负）走默认")
    void projectionDefaults() {
        AsyncTaskProfileEntity profile = new AsyncTaskProfileEntity();
        profile.setQueryPath("/v1/tasks/{task_id}");
        profile.setTaskIdPath("data.id");
        profile.setStatusPath("data.status");
        profile.setStatusSucceeded("status == 'succeed'");
        profile.setStatusFailed("status == 'failed'");
        AsyncTaskSpec spec = AsyncTaskSpec.of(profile);
        assertEquals("GET", spec.queryMethod(), "未配方法时默认 GET");
        assertEquals("status == 'succeed'", spec.statusSucceeded(),
                "快照是纯数据，表达式到求值那一步才编译");
        assertEquals(5000, spec.pollIntervalMs());
        assertEquals(1000, spec.pollBackoffBaseMs());
        assertEquals(30000, spec.pollBackoffMaxMs());
        assertEquals(360, spec.maxAttempts());
        assertEquals(1_800_000L, spec.timeoutMs());

        profile.setPollIntervalMs(0);
        profile.setPollBackoffMaxMs(-1);
        profile.setTimeoutMs(0L);
        AsyncTaskSpec sanitized = AsyncTaskSpec.of(profile);
        assertEquals(5000, sanitized.pollIntervalMs(), "0 不是合法间隔");
        assertEquals(30000, sanitized.pollBackoffMaxMs(), "负数不是合法上限");
        assertEquals(1L, sanitized.timeoutMs(), "超时必须为正，否则任务一提交就过期");
        assertNull(AsyncTaskSpec.of(null));
    }
}
