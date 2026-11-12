package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
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
                7, 5000, 1000, 30000, 360, 30L, 1, "可灵", null);
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
                null, null, null, null, null, null, null, "  备注  ", null);

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

    /**
     * 只替换档案编码、其余照抄一份合法档案：这些用例被测的就是<b>编码</b>，
     * 若每次都把 20 个字段摊开写一遍，真正的差异会被噪音淹掉。
     */
    private static AsyncTaskProfileSaveDto withCode(String profileCode) {
        AsyncTaskProfileSaveDto dto = kling("['succeed'] contains status", "status == 'failed'");
        return new AsyncTaskProfileSaveDto(profileCode, dto.protocol(),
                dto.queryMethod(), dto.queryPath(), dto.taskIdPath(), dto.statusPath(),
                dto.statusSucceeded(), dto.statusFailed(), null, dto.resultPath(),
                dto.resultUrlField(), dto.errorMessagePath(), dto.resultExpiresDays(),
                dto.pollIntervalMs(), dto.pollBackoffBaseMs(), dto.pollBackoffMaxMs(),
                dto.maxAttempts(), dto.timeoutMinutes(), dto.status(), dto.remark(), null);
    }

    @Test
    @DisplayName("⭐ 校验：档案编码大小写均可 —— ci collation + 编辑态不可改，引用漂不了")
    void profileCodeIsCaseInsensitive() {
        for (String code : List.of("KLING_TASK", "kling_task", "Kling_Task", "SeedanceVideo",
                "V1_2", "KLING-TASK", "kling-task", "video-gen-2")) {
            AsyncTaskProfileSupport.validate(withCode(code));
        }
    }

    @Test
    @DisplayName("校验：编码的形状仍要守 —— 字母开头、只含字母/数字/下划线/连字符、最长 64")
    void malformedProfileCodeRejected() {
        List<String> bad = List.of("1KLING", "-KLING", "_KLING", "KLING TASK", "KLING.TASK",
                "", "A".repeat(65));
        for (String code : bad) {
            assertTrue(assertThrows(BusinessException.class,
                            () -> AsyncTaskProfileSupport.validate(withCode(code)))
                    .getMessage().contains("档案编码"), "该被拒：" + code);
        }
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
                dto.maxAttempts(), dto.timeoutMinutes(), dto.status(), dto.remark(), null);

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
                5000, 10_000, 5_000, dto.maxAttempts(), dto.timeoutMinutes(), dto.status(), dto.remark(), null);

        assertTrue(assertThrows(BusinessException.class, () -> AsyncTaskProfileSupport.validate(bad))
                .getMessage().contains("退避上限"));
    }

    @Test
    @DisplayName("⭐ 校验：流式协议不能建异步档案 —— 下拉禁选与后端拒绝必须是同一件事")
    void streamingProtocolIsRejected() {
        // 判据同源于 ModelApiProtocolEnum#streamSupported，与绑定保存、运行期三处一致。
        // 原来只在 options 下拉里 disabled，API 仍能存进来，存下的档案永远不可能被绑定引用。
        for (ModelApiProtocolEnum protocol : ModelApiProtocolEnum.values()) {
            AsyncTaskProfileSaveDto dto = withProtocol(protocol.code());
            if (!protocol.streamSupported()) {
                AsyncTaskProfileSupport.validate(dto);
                continue;
            }
            BusinessException error = assertThrows(BusinessException.class,
                    () -> AsyncTaskProfileSupport.validate(dto), protocol.code() + " 是流式协议，必须拒绝");
            assertTrue(error.getMessage().contains("流式"),
                    protocol.code() + " 的报错要说清是流式互斥：" + error.getMessage());
        }
    }

    @Test
    @DisplayName("校验：任务超时按分钟，1~1440 之外被拒（下限不再是 1 秒）")
    void timeoutRangeIsMinutes() {
        AsyncTaskProfileSaveDto dto = valid();

        assertTrue(assertThrows(BusinessException.class, () -> AsyncTaskProfileSupport.validate(withTimeout(dto, 0L)))
                .getMessage().contains("1440"), "0 分钟必须被拒");
        assertTrue(assertThrows(BusinessException.class, () -> AsyncTaskProfileSupport.validate(withTimeout(dto, 1_441L)))
                .getMessage().contains("1440"), "超过 24 小时必须被拒");

        AsyncTaskProfileSupport.validate(withTimeout(dto, 1L));
        AsyncTaskProfileSupport.validate(withTimeout(dto, 1_440L));
    }

    /** 只换协议一列，其余照抄：被不同的就是「协议是否流式」这一个事实 */
    private static AsyncTaskProfileSaveDto withProtocol(String protocol) {
        AsyncTaskProfileSaveDto dto = kling("['succeed'] contains status", "status == 'failed'");
        return new AsyncTaskProfileSaveDto(dto.profileCode(), protocol, dto.queryMethod(),
                dto.queryPath(), dto.taskIdPath(), dto.statusPath(), dto.statusSucceeded(),
                dto.statusFailed(), dto.statusRunning(), dto.resultPath(), dto.resultUrlField(),
                dto.errorMessagePath(), dto.resultExpiresDays(), dto.pollIntervalMs(),
                dto.pollBackoffBaseMs(), dto.pollBackoffMaxMs(), dto.maxAttempts(),
                dto.timeoutMinutes(), dto.status(), dto.remark(), null);
    }

    /** 只换超时一列，其余字段照抄：这类边界用例要能一眼看出「差的是哪一个数」 */
    private static AsyncTaskProfileSaveDto withTimeout(AsyncTaskProfileSaveDto dto, Long minutes) {
        return new AsyncTaskProfileSaveDto(dto.profileCode(), dto.protocol(), dto.queryMethod(),
                dto.queryPath(), dto.taskIdPath(), dto.statusPath(), dto.statusSucceeded(),
                dto.statusFailed(), dto.statusRunning(), dto.resultPath(), dto.resultUrlField(),
                dto.errorMessagePath(), dto.resultExpiresDays(), dto.pollIntervalMs(),
                dto.pollBackoffBaseMs(), dto.pollBackoffMaxMs(), dto.maxAttempts(), minutes,
                dto.status(), dto.remark(), null);
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
        assertTrue(vo.elapsedHint().contains("命中成功"), vo.elapsedHint());
        assertTrue(vo.elapsedHint().contains("2 个产物"), vo.elapsedHint());
        assertTrue(vo.errors().isEmpty(), "合法档案不该报错：" + vo.errors());
        assertTrue(vo.warnings().isEmpty(), "成功且取到产物不该有告警：" + vo.warnings());
    }

    @Test
    @DisplayName("试跑：命中失败 → 摘要里带上游原话（运维要能直接拿着这句话去找供应商）")
    void dryRunReportsFailureWithReason() {
        String failedQuery = KLING_QUERY
                .replace("\"succeed\"", "\"failed\"")
                .replace("\"task_status_msg\":\"\"", "\"task_status_msg\":\"content moderation blocked\"");
        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(
                new AsyncTaskProfileDryRunDto(valid(), KLING_CREATE, failedQuery));

        assertEquals("FAILED", vo.terminal());
        assertEquals("content moderation blocked", vo.errorMessage());
        assertTrue(vo.elapsedHint().contains("命中失败"), vo.elapsedHint());
        assertTrue(vo.elapsedHint().contains("content moderation blocked"), vo.elapsedHint());
    }

    @Test
    @DisplayName("试跑：状态谁都不匹配且未配运行态 → 给告警（能存，但要说清会一直轮询）")
    void dryRunWarnsWhenStatusMatchesNothing() {
        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(new AsyncTaskProfileDryRunDto(
                valid(), null, KLING_QUERY.replace("\"succeed\"", "\"weird-state\"")));

        assertEquals("RUNNING", vo.terminal());
        assertTrue(vo.elapsedHint().contains("进行中"), vo.elapsedHint());
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

    @Test
    @DisplayName("⭐ 试跑：task_id_path 取不到必须记 error —— 运行时同一情形直接 502，试跑不许全绿")
    void dryRunReportsMissingUpstreamTaskId() {
        AsyncTaskProfileSaveDto dto = valid();
        AsyncTaskProfileSaveDto wrongPath = new AsyncTaskProfileSaveDto(dto.profileCode(), dto.protocol(),
                dto.queryMethod(), dto.queryPath(), "data.id", dto.statusPath(),
                dto.statusSucceeded(), dto.statusFailed(), null, dto.resultPath(),
                dto.resultUrlField(), dto.errorMessagePath(), dto.resultExpiresDays(),
                dto.pollIntervalMs(), dto.pollBackoffBaseMs(), dto.pollBackoffMaxMs(),
                dto.maxAttempts(), dto.timeoutMinutes(), dto.status(), dto.remark(), null);

        // 可灵创建响应是 data.task_id，档案却写 data.id：保存期挡不住（语法合法），
        // 只能靠试跑暴露——若这里静默给 null，运维看到「试跑通过」就会直接上线。
        AsyncTaskProfileSupport.validate(wrongPath);

        AsyncTaskProfileDryRunVo vo = AsyncTaskProfileSupport.dryRun(
                new AsyncTaskProfileDryRunDto(wrongPath, KLING_CREATE, KLING_QUERY));

        assertNull(vo.upstreamTaskId());
        assertTrue(vo.errors().stream().anyMatch(e -> e.contains("task_id_path") && e.contains("data.id")),
                "必须指出是哪一列、哪条表达式：" + vo.errors());
    }
}
