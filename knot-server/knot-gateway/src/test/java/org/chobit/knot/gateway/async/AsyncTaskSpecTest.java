package org.chobit.knot.gateway.async;

import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：查询口径快照（{@link AsyncTaskSpec}）。
 *
 * <p>快照是「同一笔任务无论何时完成都能复现」的载体：占位替换与终态取值一旦漂移，
 * 表现是任务查不到或判错终态，且金额无法复现。</p>
 */
class AsyncTaskSpecTest {

    private static AsyncTaskSpec spec(String queryPath) {
        return new AsyncTaskSpec("GET", queryPath, "data.id", null, "data[0].status",
                "succeed,SUCCEEDED", "failed,FAILED", null, "data[0].outputs[0].url",
                7, 5000, 1000, 30000, 360, 1_800_000L);
    }

    @Test
    @DisplayName("占位替换：{task_id} 与 {external_task_id}")
    void resolvesPlaceholders() {
        assertEquals("/v1/videos/generations/t-1",
                spec("/v1/videos/generations/{task_id}").resolvePath("t-1", null));
        assertEquals("/v1/tasks/k-1?by=external",
                spec("/v1/tasks/{external_task_id}?by=external").resolvePath(null, "k-1"));
    }

    @Test
    @DisplayName("占位没替换完直接抛：拼出来的是查不到东西的 URL，比明确失败难排查")
    void unresolvedPlaceholderThrows() {
        AsyncTaskSpec spec = spec("/v1/videos/{unknown}/status");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> spec.resolvePath("t-1", null));
        assertTrue(error.getMessage().contains("unresolved placeholder"), error.getMessage());
    }

    @Test
    @DisplayName("查询路径未配置时抛，不静默拼出根路径")
    void missingQueryPathThrows() {
        assertThrows(IllegalStateException.class, () -> spec(null).resolvePath("t-1", null));
    }

    @Test
    @DisplayName("终态取值多值 + 忽略大小写与空白")
    void terminalValuesMatchIgnoringCase() {
        AsyncTaskSpec spec = spec("/x/{task_id}");
        assertTrue(spec.succeeded("succeed"));
        assertTrue(spec.succeeded(" SUCCEEDED "));
        assertFalse(spec.succeeded("processing"));
        assertTrue(spec.failed("failed"));
        assertFalse(spec.failed("succeed"), "成功取值不能同时被判为失败");
    }

    @Test
    @DisplayName("statusRunning 为空 = 除成功/失败外一律非终态；有值则只认声明的那些")
    void runningSemantics() {
        assertTrue(spec("/x/{task_id}").running("queued"),
                "未声明非终态取值时，上游新增中间态不应被判成终态");
        AsyncTaskSpec explicit = new AsyncTaskSpec("GET", "/x/{task_id}", "data.id", null, "data.status",
                "succeed", "failed", "queued,processing", "data.url",
                null, 5000, 1000, 30000, 360, 1_800_000L);
        assertTrue(explicit.running("processing"));
        assertFalse(explicit.running("whatever"),
                "显式声明了非终态取值后，未声明的状态不再默认视为进行中");
    }

    @Test
    @DisplayName("档案投影：空值走默认，非法值（0/负）走默认")
    void projectionDefaults() {
        AsyncTaskProfileEntity profile = new AsyncTaskProfileEntity();
        profile.setQueryPath("/v1/tasks/{task_id}");
        profile.setTaskIdPath("data.id");
        profile.setStatusPath("data.status");
        profile.setStatusSucceeded("succeed");
        profile.setStatusFailed("failed");
        AsyncTaskSpec spec = AsyncTaskSpec.of(profile);
        assertEquals("GET", spec.queryMethod(), "未配方法时默认 GET");
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

    @Test
    @DisplayName("探针：成功集合为空时任何状态都不判成功（配置缺失不得静默放行）")
    void probeEmptySuccessSetNeverMatches() {
        AsyncTaskSpec spec = new AsyncTaskSpec("GET", "/x/{task_id}", "data.id", null, "data.status",
                "", "failed", null, "data.url", null, 5000, 1000, 30000, 360, 1_800_000L);
        assertFalse(spec.succeeded("succeed"));
        assertFalse(spec.succeeded(null));
    }
}
