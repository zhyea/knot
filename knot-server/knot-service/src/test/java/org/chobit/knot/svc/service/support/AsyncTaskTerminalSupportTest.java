package org.chobit.knot.svc.service.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：终态判定（{@link AsyncTaskTerminalSupport}）与产物/失败原因的取值口径。
 *
 * <p>⭐ 本类守的是「<b>顺序与边界</b>」，不是「能不能跑」：</p>
 * <ul>
 *   <li>成功优先于失败 —— 上游同时满足两条时（如失败响应里带上一个成功标志位），先判成功；
 *       反过来会让已成功的调用按失败结算；</li>
 *   <li>取不到状态 → RUNNING 且 {@code status} 为 {@code null}（不是空串）——
 *       「没取到」与「取到空串」在试跑里要能分开看；</li>
 *   <li>产物只在成功时取、失败原因只在失败时取 —— 失败响应里的 {@code url} 往往是半成品地址。</li>
 * </ul>
 */
class AsyncTaskTerminalSupportTest {

    private static AsyncTaskExpressions expressions(String running, String resultUrlField, String errorPath) {
        return new AsyncTaskExpressions("data.status",
                "['succeeded'] contains status",
                "['failed','cancelled'] contains status",
                running, "data.videos", resultUrlField, errorPath);
    }

    private static Map<String, Object> body(String status) {
        return Map.of("data", Map.of(
                "status", status,
                "videos", List.of(Map.of("url", "https://x/1.mp4"), Map.of("url", "https://x/2.mp4")),
                "msg", "内容不合规"));
    }

    @Test
    @DisplayName("成功：取全部产物（对象数组 + 字段名），不取失败原因")
    void succeededReadsAllUrls() {
        AsyncTaskTerminalSupport.Verdict verdict =
                AsyncTaskTerminalSupport.verdict(body("succeeded"), expressions(null, "url", "data.msg"));

        assertEquals(AsyncTaskTerminalSupport.Terminal.SUCCEEDED, verdict.terminal());
        assertEquals(List.of("https://x/1.mp4", "https://x/2.mp4"), verdict.resultUrls(),
                "n>1 的产物必须全部保留：只取 [0] 会少交付");
        assertNull(verdict.errorMessage(), "成功时不该带失败原因");
        assertTrue(verdict.hasResult());
    }

    @Test
    @DisplayName("失败：按 error_message_path 取原因，产物恒为空")
    void failedReadsErrorMessage() {
        AsyncTaskTerminalSupport.Verdict verdict =
                AsyncTaskTerminalSupport.verdict(body("failed"), expressions(null, "url", "data.msg"));

        assertEquals(AsyncTaskTerminalSupport.Terminal.FAILED, verdict.terminal());
        assertEquals("内容不合规", verdict.errorMessage());
        assertTrue(verdict.resultUrls().isEmpty(), "失败响应里的 url 是半成品，不能当交付物");
        assertFalse(verdict.hasResult());
    }

    @Test
    @DisplayName("⭐ 成功优先于失败：两条表达式同时为真时按成功结算")
    void successWinsWhenBothMatch() {
        AsyncTaskExpressions both = new AsyncTaskExpressions("status",
                "['done','done-with-error'] contains status",
                "['done-with-error'] contains status",
                null, "url", null, null);
        AsyncTaskTerminalSupport.Verdict verdict =
                AsyncTaskTerminalSupport.verdict(Map.of("status", "done-with-error"), both);

        assertEquals(AsyncTaskTerminalSupport.Terminal.SUCCEEDED, verdict.terminal());
    }

    @Test
    @DisplayName("取不到状态：RUNNING 且 status 为 null（与「取到空串」区分）")
    void missingStatusIsRunning() {
        AsyncTaskTerminalSupport.Verdict verdict =
                AsyncTaskTerminalSupport.verdict(Map.of("data", Map.of()), expressions(null, "url", null));

        assertEquals(AsyncTaskTerminalSupport.Terminal.RUNNING, verdict.terminal());
        assertNull(verdict.status());
        assertTrue(verdict.resultUrls().isEmpty());
    }

    @Test
    @DisplayName("未配运行态：除成功/失败外一律非终态（上游新增中间态不需要改档案）")
    void blankRunningMeansNonTerminal() {
        assertEquals(AsyncTaskTerminalSupport.Terminal.RUNNING,
                AsyncTaskTerminalSupport.verdict(body("queued"), expressions(null, "url", null)).terminal());
    }

    @Test
    @DisplayName("配了运行态却谁都不匹配：仍按 RUNNING（不判死），留给超时兜住")
    void unknownStatusIsRunningNotFailed() {
        AsyncTaskExpressions withRunning = new AsyncTaskExpressions("data.status",
                "['succeeded'] contains status", "['failed'] contains status",
                "['queued','running'] contains status", "data.videos", "url", null);

        assertEquals(AsyncTaskTerminalSupport.Terminal.RUNNING,
                AsyncTaskTerminalSupport.verdict(body("brand-new"), withRunning).terminal());
    }
}
