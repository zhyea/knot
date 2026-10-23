package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.model.AudioBillingUsage;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.ImageBillingUsage;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.VideoBillingUsage;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.VideoBillingModeCalculator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UsageExtractor} 泛型化后的按模式提取行为。
 *
 * <p>覆盖：四种用量记录各自的属性来源（响应体 / 请求体）、SSE 逐事件合并、
 * 空用量兜底，以及「提取器类型 ↔ 计费模式」的一一对应。
 * 2026-10-23 双视图一次提取后，提取器返回 {@link UsageExtractItem}，计费半边取 {@code billing()}。</p>
 */
class UsageExtractorTest {

    private final DefaultTokenUsageExtractor defaultExtractor = new DefaultTokenUsageExtractor(new TokenBillingModeCalculator());
    private final AnthropicUsageExtractor anthropicExtractor = new AnthropicUsageExtractor(new TokenBillingModeCalculator());
    private final ImageUsageExtractor imageExtractor = new ImageUsageExtractor(new SimpleBillingModeCalculator());
    private final VideoUsageExtractor videoExtractor = new VideoUsageExtractor(new VideoBillingModeCalculator());
    private final AudioUsageExtractor audioExtractor = new AudioUsageExtractor(new SimpleBillingModeCalculator());
    private final BedrockConverseUsageExtractor bedrockExtractor =
            new BedrockConverseUsageExtractor(new TokenBillingModeCalculator());

    @Test
    void shouldExtractBedrockCamelCaseUsage() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("usage", Map.of(
                "inputTokens", 120, "outputTokens", 30, "totalTokens", 150,
                "cacheReadInputTokens", 10, "cacheWriteInputTokens", 5));

        TokenBillingUsage usage = bedrockExtractor.extractUsage(body).billing();

        assertEquals(120L, usage.inputTokens());
        assertEquals(30L, usage.outputTokens());
        assertEquals(150L, usage.totalTokens());
        assertEquals(10L, usage.cacheReadTokens());
        assertEquals(5L, usage.cacheWriteTokens());
        assertFalse(usage.isEmpty());
    }

    @Test
    void shouldNotReadCamelCaseUsageByDefaultExtractor() {
        Map<String, Object> body = Map.of("usage", Map.of("inputTokens", 120, "outputTokens", 30));

        assertTrue(defaultExtractor.extractUsage(body).billing().isEmpty(),
                "默认提取器只认 snake_case——这是 BEDROCK_CONVERSE 提取器存在的理由");
    }

    private final EmptyAllowedTokenUsageExtractor emptyAllowedExtractor =
            new EmptyAllowedTokenUsageExtractor(new TokenBillingModeCalculator());

    @Test
    void shouldDeclareEmptyAllowedOnTolerantExtractorOnly() {
        assertFalse(defaultExtractor.allowsEmptyUsage(),
                "默认提取器未声明允许 → 有正数却读不出应判口径错配");
        assertTrue(emptyAllowedExtractor.allowsEmptyUsage(),
                "容忍空用量的提取器必须显式声明，不能靠注册表全局猜");
    }

    @Test
    void shouldShareTokenUsageSemanticsWithDefaultExtractor() {
        Map<String, Object> body = Map.of("usage", Map.of(
                "prompt_tokens", 700, "completion_tokens", 200, "total_tokens", 900));

        assertEquals(defaultExtractor.extractUsage(body).billing(),
                emptyAllowedExtractor.extractUsage(body).billing(),
                "两者取量口径必须完全一致，差别只在是否允许空用量");
    }

    // ---------- DEFAULT / ANTHROPIC：token 口径 ----------

    @Test
    void shouldExtractTokenUsageFromResponseBody() {
        Map<String, Object> body = Map.of("usage", Map.of(
                "prompt_tokens", 700,
                "completion_tokens", 200,
                "total_tokens", 900,
                "prompt_tokens_details", Map.of("cached_tokens", 300)));

        TokenBillingUsage usage = defaultExtractor.extractUsage(body).billing();

        assertEquals(700L, usage.inputTokens());
        assertEquals(200L, usage.outputTokens());
        assertEquals(900L, usage.totalTokens());
        assertEquals(300L, usage.cacheReadTokens());
        assertFalse(usage.isEmpty());
    }

    @Test
    void shouldExtractDualViewFromResponseBody() {
        Map<String, Object> body = Map.of("usage", Map.of(
                "prompt_tokens", 700,
                "completion_tokens", 200,
                "total_tokens", 900,
                "prompt_tokens_details", Map.of("cached_tokens", 300),
                "completion_tokens_details", Map.of("reasoning_tokens", 40)));

        UsageExtractItem<TokenBillingUsage> item = defaultExtractor.extractUsage(body);

        assertNotNull(item.usage());
        // 双视图不变量：计费输入总量 == 展示视图输入总量
        assertEquals(item.billing().inputTokens(), item.usage().input().tokens().total());
        assertEquals(item.billing().outputTokens(), item.usage().output().tokens().total());
        assertEquals(40L, item.usage().output().tokens().reasoning());
    }

    @Test
    void shouldReturnEmptyTokenUsageWhenUsageAbsent() {
        assertTrue(defaultExtractor.extractUsage(Map.of("id", "1")).billing().isEmpty());
        assertTrue(defaultExtractor.extractUsage(Map.of()).billing().isEmpty());
        assertEquals(TokenBillingUsage.empty(), defaultExtractor.empty().billing());
        assertNull(defaultExtractor.empty().usage());
    }

    @Test
    void shouldAddCacheBackToInputForAnthropic() {
        Map<String, Object> body = Map.of("usage", Map.of(
                "input_tokens", 700,
                "output_tokens", 200,
                "cache_read_input_tokens", 300,
                "cache_creation", Map.of("ephemeral_5m_input_tokens", 400)));

        TokenBillingUsage usage = anthropicExtractor.extractUsage(body).billing();

        // Anthropic 口径：input_tokens 不含缓存读写，加回后 700 + 300 + 400 = 1400
        assertEquals(1400L, usage.inputTokens());
        assertEquals(400L, usage.cacheWrite5mTokens());
        assertEquals(1600L, usage.totalTokens());
    }

    // ---------- IMAGE：张数 + 像素 ----------

    @Test
    void shouldExtractImageCountFromDataArrayAndPixelsFromRequest() {
        Map<String, Object> body = Map.of("data", List.of(
                Map.of("url", "a"), Map.of("url", "b"), Map.of("url", "c")));
        Map<String, Object> request = Map.of("size", "1024x1024");

        ImageBillingUsage usage = imageExtractor.extractUsage(body, request).billing();

        assertEquals(3L, usage.count());
        assertEquals(1024L, usage.width());
        assertEquals(1024L, usage.height());
        assertEquals(1_048_576L, usage.pixels());
        assertTrue(usage.tokens().isEmpty(), "纯媒体响应内嵌 token 为空");
    }

    @Test
    void shouldFallBackToUsageAliasesForImageCount() {
        Map<String, Object> body = Map.of("usage", Map.of("image_count", 2));

        ImageBillingUsage usage = imageExtractor.extractUsage(body).billing();

        assertEquals(2L, usage.count());
    }

    @Test
    void shouldKeepTokenUsageInsideImageUsage() {
        // gpt-image-1 类上游：usage 带 token，data 数组带张数——两者都要留住
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("usage", Map.of("total_tokens", 500, "input_tokens", 400, "output_tokens", 100));
        body.put("data", List.of(Map.of("b64_json", "x"), Map.of("b64_json", "y")));

        ImageBillingUsage usage = imageExtractor.extractUsage(body, Map.of("size", "1024x1792")).billing();

        assertEquals(2L, usage.count());
        assertEquals(500L, usage.tokens().totalTokens());
        assertEquals(1024L, usage.width());
        assertEquals(1792L, usage.height());
    }

    @Test
    void shouldParsePresetSizeNames() {
        ImageBillingUsage usage = imageExtractor.extractUsage(Map.of(), Map.of("size", "720p")).billing();
        assertEquals(1280L, usage.width());
        assertEquals(720L, usage.height());
        assertEquals(921_600L, usage.pixels());

        // 无法识别的尺寸（auto）不阻塞张数提取
        ImageBillingUsage auto = imageExtractor.extractUsage(
                Map.of("data", List.of(Map.of("url", "a"))), Map.of("size", "auto")).billing();
        assertEquals(1L, auto.count());
        assertEquals(0L, auto.pixels());
    }

    // ---------- VIDEO：时长 + 像素 + 声音 + 参考 ----------

    @Test
    void shouldExtractVideoDurationFromBodyAndAttributesFromRequest() {
        Map<String, Object> body = Map.of("duration_seconds", 10);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", "1280x720");
        request.put("generate_audio", true);
        request.put("input_reference", Map.of("file_id", "f-1"));

        VideoBillingUsage usage = videoExtractor.extractUsage(body, request).billing();

        assertEquals(10L, usage.durationSeconds());
        assertEquals(1280L, usage.width());
        assertEquals(720L, usage.height());
        assertEquals(921_600L, usage.pixels());
        assertTrue(usage.sound());
        assertTrue(usage.reference());
    }

    @Test
    void shouldPreferUsageAliasesForVideoDuration() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("usage", Map.of("seconds", 8));
        body.put("duration_seconds", 99);

        VideoBillingUsage usage = videoExtractor.extractUsage(body, Map.of()).billing();

        assertEquals(8L, usage.durationSeconds(), "usage 里的秒数优先于响应体顶层");
    }

    @Test
    void shouldDefaultSoundAndReferenceToFalse() {
        VideoBillingUsage usage = videoExtractor.extractUsage(
                Map.of("video_seconds", 5), Map.of("prompt", "a cat")).billing();

        assertEquals(5L, usage.durationSeconds());
        assertFalse(usage.sound());
        assertFalse(usage.reference());
    }

    // ---------- AUDIO：时长 ----------

    @Test
    void shouldExtractAudioDuration() {
        Map<String, Object> body = Map.of("usage", Map.of("seconds", 95));

        AudioBillingUsage usage = audioExtractor.extractUsage(body).billing();

        assertEquals(95L, usage.durationSeconds());
        // 上游只报秒数：内嵌 token 用量没有任何 token 量（秒数被 amount 别名收编，不参与 token 计费）
        assertEquals(0L, usage.tokens().totalTokens());
    }

    @Test
    void shouldFallBackToBodyForAudioDuration() {
        // 转写类接口：usage 缺失，时长在响应体顶层
        AudioBillingUsage usage = audioExtractor.extractUsage(Map.of("duration", 12), Map.of()).billing();

        assertEquals(12L, usage.durationSeconds());
    }

    // ---------- 媒体双视图：计时计量进 usage ----------

    @Test
    void shouldProjectMediaMeteringIntoUsageView() {
        Map<String, Object> body = Map.of("duration_seconds", 10);
        UsageExtractItem<VideoBillingUsage> item = videoExtractor.extractUsage(body, Map.of());

        assertNotNull(item.usage());
        assertEquals(10L, item.usage().output().videoSeconds());
        assertEquals(0L, item.usage().totalTokens());
    }

    // ---------- SSE 逐事件合并 ----------

    @Test
    void shouldMergeEventStreamUsageByMax() {
        String sse = """
                data: {"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}

                data: {"usage":{"prompt_tokens":30,"completion_tokens":9,"total_tokens":39}}

                data: [DONE]
                """;

        BillingUsage merged = defaultExtractor.extractUsageBody(sse, Map.of()).billing();

        assertInstanceOf(TokenBillingUsage.class, merged);
        TokenBillingUsage token = (TokenBillingUsage) merged;
        assertEquals(30L, token.inputTokens());
        assertEquals(9L, token.outputTokens());
        assertEquals(39L, token.totalTokens());
    }

    @Test
    void shouldMergeMediaUsageAcrossEvents() {
        String sse = """
                data: {"duration_seconds":4}

                data: {"duration_seconds":11}

                data: [DONE]
                """;

        BillingUsage merged = videoExtractor.extractUsageBody(sse, Map.of("size", "1080p")).billing();

        assertInstanceOf(VideoBillingUsage.class, merged);
        VideoBillingUsage video = (VideoBillingUsage) merged;
        assertEquals(11L, video.durationSeconds());
        assertEquals(1920L, video.width());
        assertEquals(1080L, video.height());
    }

    // ---------- 空响应兜底 ----------

    @Test
    void shouldReturnEmptyUsageForBlankBody() {
        assertEquals(ImageBillingUsage.empty(), imageExtractor.extractUsageBody("  ", Map.of()).billing());
        assertEquals(AudioBillingUsage.empty(), audioExtractor.extractUsageBody(null, Map.of()).billing());
        assertEquals(VideoBillingUsage.empty(), videoExtractor.extractUsageBody("not json", Map.of()).billing());
    }
}
