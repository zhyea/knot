package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.adapter.usage.calculator.VideoBillingModeCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：可灵视频用量提取（{@link KlingTurboVideoUsageExtractor}）。
 *
 * <p>⭐ 守的核心：<b>取不到产物时长就是 0，绝不兜成 1 秒</b>。
 * 「少收 1 秒」看着像保守，实则是把「上游没生成出来」记成「生成了但很短」，
 * 事后既查不出来也追不回。这类兜底在异步链路上尤其致命，因为没人盯着响应体看。</p>
 */
class KlingTurboVideoUsageExtractorTest {

    private final KlingTurboVideoUsageExtractor extractor =
            new KlingTurboVideoUsageExtractor(new VideoBillingModeCalculator());

    /** 真实查询响应的形状：data 是数组，outputs 在数组第一项里，duration 是字符串秒 */
    private static Map<String, Object> body(String status, List<Map<String, Object>> outputs) {
        return Map.of("code", 0, "data", List.of(Map.of("id", "t-1", "status", status, "outputs", outputs)));
    }

    @Test
    @DisplayName("产物时长：data[0].outputs[0].duration 是字符串秒 → 毫秒")
    void durationStringToMillis() {
        Map<String, Object> body = body("succeeded",
                List.of(Map.of("type", "video", "url", "https://x/a.mp4", "duration", "10")));
        assertEquals(10_000L, extractor.extractUsage(body, Map.of()).billing().outputVideoMillis());
        assertEquals(10L, extractor.extractUsage(body, Map.of()).billing().outputSeconds());
    }

    @Test
    @DisplayName("小数秒按秒→毫秒换算保留精度，计费口径仍是向上取整秒（与通用提取器同口径）")
    void fractionalSeconds() {
        Map<String, Object> body = body("succeeded", List.of(Map.of("type", "video", "duration", "10.4")));
        assertEquals(10_400L, extractor.extractUsage(body, Map.of()).billing().outputVideoMillis());
        assertEquals(11L, extractor.extractUsage(body, Map.of()).billing().outputSeconds());
    }

    @Test
    @DisplayName("outputs 为空 → 0ms，不兜底成 1 秒")
    void missingOutputsYieldsZero() {
        Map<String, Object> body = body("succeeded", List.of());
        assertEquals(0L, extractor.extractUsage(body, Map.of()).billing().outputVideoMillis());
    }

    @Test
    @DisplayName("探针：顶层 duration 不得被当成产物时长（通用键表的形状在本提取器里必须失效）")
    void probeTopLevelDurationIsIgnored() {
        Map<String, Object> withTopLevel = Map.of("duration", "99", "data", List.of(Map.of("outputs", List.of())));
        assertEquals(0L, extractor.extractUsage(withTopLevel, Map.of()).billing().outputVideoMillis(),
                "顶层 duration 绝不能被当成产物时长");
    }

    @Test
    @DisplayName("多个产物时取第一个 type=video 的；非视频产物跳过")
    void picksVideoOutput() {
        Map<String, Object> body = body("succeeded", List.of(
                Map.of("type", "watermark_image", "duration", "999"),
                Map.of("type", "video", "duration", "5")));
        assertEquals(5_000L, extractor.extractUsage(body, Map.of()).billing().outputVideoMillis());
    }

    @Test
    @DisplayName("未标 type 的产物按视频处理（该位置只可能是视频）")
    void untypedOutputCounts() {
        Map<String, Object> body = body("succeeded", List.of(Map.of("duration", "7")));
        assertEquals(7_000L, extractor.extractUsage(body, Map.of()).billing().outputVideoMillis());
    }

    @Test
    @DisplayName("有声恒 true、参考视频恒 false：模型事实，不看请求体")
    void modelFactsOverrideRequest() {
        var billing = extractor.extractUsage(body("succeeded", List.of()), Map.of()).billing();
        assertTrue(billing.sound(), "3.0 Turbo 只有有声一档");
        assertFalse(billing.referenceVideo(), "无参考视频能力，首帧是图片不是视频");
        assertFalse(billing.hasInputVideo(), "既无输入时长也无参考视频");
    }

    @Test
    @DisplayName("探针：客户端传了同名的 video / audio 字段也不得翻转这两维")
    void probeRequestCannotOverrideModelFacts() {
        var poisoned = extractor.extractUsage(body("succeeded", List.of()),
                Map.of("video", "https://x/in.mp4", "generate_audio", false)).billing();
        assertFalse(poisoned.referenceVideo());
        assertTrue(poisoned.sound());
        assertFalse(poisoned.hasInputVideo());
    }

    @Test
    @DisplayName("分辨率走通用键表：size 按档位名解析（不反推宽高，也不是 Kling 的 settings.resolution）")
    void resolutionFromClientContract() {
        assertEquals("720P",
                extractor.extractUsage(Map.of(), Map.of("size", "720p")).billing().outputResolution());
        assertEquals("1080P",
                extractor.extractUsage(Map.of(), Map.of("size", "1080p")).billing().outputResolution());
    }

    @Test
    @DisplayName("探针：上游形态的键必须命中不了（否则就是全线静默漏收）")
    void probeUpstreamShapedKeyNeverMatches() {
        assertEquals("", extractor.extractUsage(Map.of(),
                Map.of("settings", Map.of("resolution", "1080p"))).billing().outputResolution());
    }

    @Test
    @DisplayName("输入图片：image 非空记 1 张（事实展示，不产生金额分量）")
    void inputImageCount() {
        assertEquals(1L, extractor.extractUsage(Map.of(), Map.of("image", "https://x/f.png"))
                .billing().inputImageCount());
        assertEquals(1L, extractor.extractUsage(Map.of(), Map.of("image", List.of("https://x/f.png")))
                .billing().inputImageCount());
        assertEquals(0L, extractor.extractUsage(Map.of(), Map.of("prompt", "一只猫"))
                .billing().inputImageCount());
    }

    @Test
    @DisplayName("输出条数沿用通用口径：单任务查询的 data 长度为 1")
    void videoCountIsOne() {
        Map<String, Object> body = body("succeeded", List.of(Map.of("type", "video", "duration", "10")));
        assertEquals(1L, extractor.extractUsage(body, Map.of()).billing().outputVideoCount());
    }

    @Test
    @DisplayName("身份：CODE / 计算器 / 计费模式 / 空用量不放行")
    void identity() {
        assertEquals("KLING_TURBO_VIDEO", extractor.code());
        assertTrue(extractor.calculator() instanceof VideoBillingModeCalculator);
        assertEquals(1, extractor.billingModes().size());
        assertFalse(extractor.allowsEmptyUsage(), "维持默认：上游有正数却提取为空应判定为口径错配");
    }
}
