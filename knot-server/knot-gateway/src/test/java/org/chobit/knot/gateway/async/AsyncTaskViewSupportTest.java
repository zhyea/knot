package org.chobit.knot.gateway.async;

import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 异步任务终态投影的<b>下发条件</b>（2026-11-12 robin 拍板口径）。
 *
 * <p>判据不是「某个方法被调用」，而是四个出口各自的 wire 结果：
 * {@code knot_extend} / 并列 {@code usage} 只在「终态 + 消费者配置」或测试头下出现，
 * {@code upstream_body} 只跟测试头。回调与轮询共用 {@link AsyncTaskViewSupport}，
 * 所以这里验的也就是回调 body 的形状。</p>
 */
class AsyncTaskViewSupportTest {

    /** 顶层带 usage（含上游 cost）的终态查询响应原文 */
    private static final String TERMINAL_BODY =
            "{\"usage\":{\"cost\":\"0.12\",\"total_tokens\":100},\"data\":{\"task_status\":\"succeed\"}}";

    private static final String SETTLEMENT = JsonKit.toJson(
            new KnotExtendPayload(null, new NormalizedUsage(1500L, "0.004", "USD", "v1", List.of())));

    @Test
    @DisplayName("终态 + consumer 配置：下发 knot_extend 与并列 usage，但不带 upstream_body")
    void terminalWithConsumerConfigReturnsAccountingPair() {
        AsyncTaskQueryResponse response = AsyncTaskViewSupport.toResponse(
                task(AsyncTaskStatusEnum.SUCCEEDED.code(), context(true), "{}", TERMINAL_BODY, SETTLEMENT));

        assertNotNull(response.knotExtend(), "账目在终态结算时已落 settlement_json，缺的是下发开关");
        assertNotNull(response.rawUsage(), "并列 usage 是上游原始用量，与 knot_extend 成对");
        assertEquals("0.12", response.rawUsage().get("cost"), "上游 cost 口径要能直接从并列 usage 读到");
        assertNull(response.upstreamBody(), "consumer 配置只给账目，不该把上游响应原文一起泄露出去");
    }

    @Test
    @DisplayName("终态但 consumer 未开：三键全缺")
    void terminalWithoutConsumerConfigHasNoEvidence() {
        AsyncTaskQueryResponse response = AsyncTaskViewSupport.toResponse(
                task(AsyncTaskStatusEnum.SUCCEEDED.code(), context(false), "{}", TERMINAL_BODY, SETTLEMENT));

        assertNull(response.knotExtend());
        assertNull(response.rawUsage());
        assertNull(response.upstreamBody());
    }

    @Test
    @DisplayName("在途 + consumer 已开：仍不下发（账还没算出来）")
    void inFlightNeverReturnsAccounting() {
        AsyncTaskQueryResponse response = AsyncTaskViewSupport.toResponse(
                task(AsyncTaskStatusEnum.POLLING.code(), context(true), "{}", TERMINAL_BODY, SETTLEMENT));

        assertNull(response.knotExtend(), "在途任务 settlement_json 还没写，下发只会给客户端一个空账");
        assertNull(response.rawUsage());
        assertNull(response.upstreamBody());
    }

    @Test
    @DisplayName("测试头单独叠加：三键全出（含上游原文）")
    void testHeaderAloneReturnsAllEvidence() {
        AsyncTaskQueryResponse response = AsyncTaskViewSupport.toResponse(
                task(AsyncTaskStatusEnum.SUCCEEDED.code(), context(false), "{}", TERMINAL_BODY, SETTLEMENT),
                true);

        assertNotNull(response.knotExtend());
        assertNotNull(response.rawUsage());
        assertNotNull(response.upstreamBody(), "upstream_body 是排障证据，只跟测试头");
    }

    @Test
    @DisplayName("未结算（失败/超时）：两键成对缺席，不许 usage 单飞")
    void missingSettlementKeepsPairAbsent() {
        AsyncTaskQueryResponse response = AsyncTaskViewSupport.toResponse(
                task(AsyncTaskStatusEnum.FAILED.code(), context(true), "{}", TERMINAL_BODY, null));

        assertNull(response.knotExtend());
        assertNull(response.rawUsage(), "账目不存在时并列 usage 也不出现，与同步链路同口径");
    }

    @Test
    @DisplayName("旧快照缺 usagePath 时回落顶层 usage；配了就按表达式取嵌套对象")
    void usagePathDrivesRawUsageExtraction() {
        String nestedSpec = "{\"usagePath\":\"data.usage\"}";
        String nestedBody = "{\"data\":{\"usage\":{\"cost\":\"0.8\"}}}";

        AsyncTaskQueryResponse fallback = AsyncTaskViewSupport.toResponse(
                task(AsyncTaskStatusEnum.SUCCEEDED.code(), context(true), "{}", TERMINAL_BODY, SETTLEMENT));
        assertEquals("0.12", fallback.rawUsage().get("cost"), "旧快照没有 usagePath → 顶层 usage 兜底");

        AsyncTaskQueryResponse nested = AsyncTaskViewSupport.toResponse(
                task(AsyncTaskStatusEnum.SUCCEEDED.code(), context(true), nestedSpec, nestedBody, SETTLEMENT));
        assertEquals("0.8", nested.rawUsage().get("cost"), "配了 usagePath 就从嵌套路径取供应商成本口径");
    }

    /** 提交时快照：旧 context_json 没有该字段时 Jackson 填 false，由 {@link #task} 的变体覆盖。 */
    private static String context(boolean returnUsageDetail) {
        return JsonKit.toJson(new AsyncTaskContext("tc-1", "rule-1", null, null, "req-1",
                "VIDEO_GENERATIONS", "model-1", "upstream-1", "provider-1", null, null,
                null, null, null, null, returnUsageDetail));
    }

    /** 30 组件的任务行；只把本测试关心的五列参数化，其余照既有测试的形态填。 */
    private static AsyncTaskDto task(int status, String contextJson, String pollSpecJson,
                                     String terminalBody, String settlementJson) {
        LocalDateTime submitted = LocalDateTime.of(2026, 11, 12, 12, 0);
        return new AsyncTaskDto(7L, "tc-1", "KLING_TASK", "VIDEO_GENERATIONS", "up-1",
                status, 1, 0, null, null,
                "{\"prompt\":\"a cat\"}", pollSpecJson, contextJson,
                "{\"result_url\":\"https://x/a.mp4\"}",
                terminalBody, settlementJson, null, null, null, null,
                submitted, submitted.plusMinutes(1),
                submitted.plusMinutes(30), submitted.plusDays(1),
                "https://client.example/cb", 0, 0, null, null, null);
    }
}
