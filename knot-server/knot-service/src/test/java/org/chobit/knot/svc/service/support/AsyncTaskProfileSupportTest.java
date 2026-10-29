package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDryRunDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileSaveDto;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDryRunVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：档案的静态校验与试跑（{@link AsyncTaskProfileSupport}）。
 *
 * <p>⭐ <b>两条错误策略必须一起守</b>：校验是「非法就 400」，试跑是「错了也要看得见」。
 * 把两者混在一个端点里，必然有一方被牺牲——这正是它们分成两个方法的原因。</p>
 *
 * <p>⚠ 用例里的响应体是<b>可灵 v1 的真实形状</b>（{@code data.task_status} +
 * {@code data.task_result.videos[].url}）。写成虚构体就测不出「数组里套对象」这个最容易配错的点。</p>
 */
class AsyncTaskProfileSupportTest {

    /** 可灵 text2video 的查询响应（截取与档案相关的部分） */
    private static final String KLING_QUERY = """
            {"code":0,"message":"SUCCEED","request_id":"r1",
             "data":{"task_id":"k-1","task_status":"succeed","task_status_msg":"",
                     "task_result":{"videos":[{"id":"v1","url":"https://x/1.mp4"},
                                             {"id":"v2","url":"https://x/2.mp4"}]}}}
            """;

    private static final String KLING_CREATE = """
            {"code":0,"message":"SUCCEED","data":{"task_id":"k-1","task_status":"submitted"}}
            """;

    private static AsyncTaskProfileSaveDto kling(String statusSucceeded, String statusFailed) {
        return new AsyncTaskProfileSaveDto(
                "KLING_TASK", "VIDEO_GENERATIONS", "GET", "/v1/videos/text2video/{task_id}",
                "data.task_id", "data.task_status",
                statusSucceeded, statusFailed, null,
                "data.task_result.videos", "url", "data.task_status_msg",
                7, 5000, 1000, 30000, 360, 1_800_000L, 1, "可灵");
    }

    private static AsyncTaskProfileSaveDto valid() {
        return kling("['succeed','success'] contains status", "status == 'failed'");
    }

    @Test
    @DisplayName("归一：协议与方法转大写、首尾空白去掉、空串折成 null")
    void normalizeTrimsAndUppercases() {
        AsyncTaskProfileSaveDto raw = new AsyncTaskProfileSaveDto(
                "  KLING_TASK ", " video_generations ", " get ", " /v1/tasks/{task_id} ",
                " data.task_id ", " data.status ", "status == 'ok'", "status == 'bad'",
                "   ", " data.url ", null, "  ",
                null, null, null, null, null, null, null, "  备注  ");

        AsyncTaskProfileSaveDto dto = AsyncTaskProfileSupport.normalize(raw);

        assertEquals("KLING_TASK", dto.profileCode());
        assertEquals("VIDEO_GENERATIONS", dto.protocol());
        assertEquals("GET", dto.queryMethod());
        assertNull(dto.statusRunning(), "空白字符串必须折成 null：可选档位用 null 表示「没配」");
        assertNull(dto.errorMessagePath());
        assertEquals("备注", dto.remark());
        assertEquals(1, dto.status(), "未指定状态时默认启用");
    }

    @Test
    @DisplayName("校验：合法档案直接通过")
    void validProfilePasses() {
        AsyncTaskProfileSupport.validate(valid());
    }

    @Test
    @DisplayName("校验：编码必须大写开头，否则绑定表引用会与人工输入漂移")
    void lowerCaseCodeRejected() {
        AsyncTaskProfileSaveDto dto = kling("['succeed'] contains status", "status == 'failed'");
        AsyncTaskProfileSaveDto bad = new AsyncTaskProfileSaveDto("kling_task", dto.protocol(),
                dto.queryMethod(), dto.queryPath(), dto.taskIdPath(), dto.statusPath(),
                dto.statusSucceeded(), dto.statusFailed(), null, dto.resultPath(),
                dto.resultUrlField(), dto.errorMessagePath(), dto.resultExpiresDays(),
                dto.pollIntervalMs(), dto.pollBackoffBaseMs(), dto.pollBackoffMaxMs(),
                dto.maxAttempts(), dto.timeoutMs(), dto.status(), dto.remark());

        assertTrue(assertThrows(BusinessException.class, () -> AsyncTaskProfileSupport.validate(bad))
                .getMessage().contains("档案编码"));
    }

    @Test
    @DisplayName("校验：查询路径必须带 {task_id}，否则每次都在查同一个地址")
    void queryPathWithoutPlaceholderRejected() {
        AsyncTaskProfileSaveDto dto = valid();
        AsyncTaskProfileSaveDto bad = new AsyncTaskProfileSaveDto(dto.profileCode(), dto.protocol(),
                dto.queryMethod(), "/v1/videos/text2video", dto.taskIdPath(), dto.statusPath(),
                dto.statusSucceeded(), dto.statusFailed(), null, dto.resultPath(),
                dto.resultUrlField(), dto.errorMessagePath(), dto.resultExpiresDays(),
                dto.pollIntervalMs(), dto.pollBackoffBaseMs(), dto.pollBackoffMaxMs(),
                dto.maxAttempts(), dto.timeoutMs(), dto.status(), dto.remark());

        assertTrue(assertThrows(BusinessException.class, () -> AsyncTaskProfileSupport.validate(bad))
                .getMessage().contains("{task_id}"));
    }

    @Test
    @DisplayName("⭐ 探针：旧逗号列表<b>能编译</b>，保存期挡不住 —— 必须由试跑暴露")
    void legacyCommaListOnlyCaughtByDryRun() {
        AsyncTaskProfileSaveDto legacy = kling("succeed,success", "failed");

        // MVEL 把 `a,b` 当成语句序列，编译期不报错（§8.14 实测结论）。
        // 所以「保存时校验」只能挡语法错，语义错必须靠试跑——这条用例就是那条边界。
        AsyncTaskProfileSupport.validate(legacy);

        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(
                new AsyncTaskProfileDryRunDto(legacy, null, KLING_QUERY));
        assertFalse(vo.errors().isEmpty(), "逗号列表必须在这里被抓住，否则任务会永远 RUNNING 到超时");
        assertTrue(vo.errors().get(0).contains("无法求值"), vo.errors().toString());
        assertNull(vo.terminal());
    }

    @Test
    @DisplayName("校验：退避上限小于基数会被拒（那样每次都被基数顶住，上限形同虚设）")
    void backoffMaxBelowBaseRejected() {
        AsyncTaskProfileSaveDto dto = valid();
        AsyncTaskProfileSaveDto bad = new AsyncTaskProfileSaveDto(dto.profileCode(), dto.protocol(),
                dto.queryMethod(), dto.queryPath(), dto.taskIdPath(), dto.statusPath(),
                dto.statusSucceeded(), dto.statusFailed(), null, dto.resultPath(),
                dto.resultUrlField(), dto.errorMessagePath(), dto.resultExpiresDays(),
                5000, 10_000, 5_000, dto.maxAttempts(), dto.timeoutMs(), dto.status(), dto.remark());

        assertTrue(assertThrows(BusinessException.class, () -> AsyncTaskProfileSupport.validate(bad))
                .getMessage().contains("退避上限"));
    }

    @Test
    @DisplayName("试跑：可灵响应 → 取到任务 ID、判定成功、拿到两个产物")
    void dryRunKlingSucceeds() {
        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(
                new AsyncTaskProfileDryRunDto(valid(), KLING_CREATE, KLING_QUERY));

        assertEquals("k-1", vo.upstreamTaskId());
        assertEquals("succeed", vo.status());
        assertEquals("SUCCEEDED", vo.terminal());
        assertEquals(List.of("https://x/1.mp4", "https://x/2.mp4"), vo.resultUrls());
        assertTrue(vo.errors().isEmpty(), "合法档案不该报错：" + vo.errors());
        assertTrue(vo.warnings().isEmpty(), "成功且取到产物不该有告警：" + vo.warnings());
    }

    @Test
    @DisplayName("试跑：状态谁都不匹配且未配运行态 → 给告警（能存，但要说清会一直轮询）")
    void dryRunWarnsWhenStatusMatchesNothing() {
        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(new AsyncTaskProfileDryRunDto(
                valid(), null, KLING_QUERY.replace("\"succeed\"", "\"weird-state\"")));

        assertEquals("RUNNING", vo.terminal());
        assertFalse(vo.warnings().isEmpty(), "会一直轮询到超时的配置必须提示");
        assertTrue(vo.warnings().get(0).contains("weird-state"), vo.warnings().toString());
    }

    @Test
    @DisplayName("试跑：表达式写错 → 收进 errors 且不判终态（不抛，让人一次看到全部问题）")
    void dryRunCollectsErrors() {
        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(new AsyncTaskProfileDryRunDto(
                kling("status ==", "status == 'failed'"), null, KLING_QUERY));

        assertFalse(vo.errors().isEmpty());
        assertNull(vo.terminal(), "有错时不该给出判定结论");
        assertTrue(vo.elapsedHint().contains("未能判定"), vo.elapsedHint());
    }

    @Test
    @DisplayName("试跑：响应不是合法 JSON → errors 里说清是哪一段")
    void dryRunRejectsInvalidJson() {
        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(
                new AsyncTaskProfileDryRunDto(valid(), "not-json", KLING_QUERY));

        assertTrue(vo.errors().stream().anyMatch(e -> e.contains("创建响应")), vo.errors().toString());
    }
}
