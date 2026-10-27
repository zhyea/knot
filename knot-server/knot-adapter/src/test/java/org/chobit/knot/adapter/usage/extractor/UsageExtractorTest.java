package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.adapter.model.usage.AudioBillingUsage;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.UsageExtractItem;
import org.chobit.knot.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.SimpleBillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.TokenBillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.VideoBillingModeCalculator;
import org.chobit.knot.adapter.usage.extractor.*;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        assertEquals(10L, usage.outputSeconds());
        assertEquals("720P", usage.outputResolution(), "1280x720 归一化为档位名 720P");
        assertTrue(usage.sound());
        assertTrue(usage.referenceVideo());
    }

    @Test
    void shouldNormalizeDeclaredResolutionCaseAndSeparators() {
        // 厂商写法五花八门：768p / 768P / "768 P" / 768-P / 768_p 都应落到同一个档位键。
        // 归一化只在这里做一遍——计算器直接消费归一化后的档位名，不重复解析。
        for (String raw : new String[]{"768p", "768P", "768 P", "768-P", "768_p"}) {
            VideoBillingUsage usage = videoExtractor.extractUsage(
                    Map.of("duration_seconds", 5), Map.of("resolution", raw)).billing();

            assertEquals("768P", usage.outputResolution(), "写法 " + raw + " 应归一到 768P");
        }
    }

    @Test
    void shouldPreferUsageAliasesForVideoDuration() {        Map<String, Object> body = new LinkedHashMap<>();
        body.put("usage", Map.of("seconds", 8));
        body.put("duration_seconds", 99);

        VideoBillingUsage usage = videoExtractor.extractUsage(body, Map.of()).billing();

        assertEquals(8L, usage.outputSeconds(), "usage 里的秒数优先于响应体顶层");
    }

    @Test
    void shouldDefaultSoundAndReferenceToFalse() {
        VideoBillingUsage usage = videoExtractor.extractUsage(
                Map.of("video_seconds", 5), Map.of("prompt", "a cat")).billing();

        assertEquals(5L, usage.outputSeconds());
        assertFalse(usage.sound());
        assertFalse(usage.referenceVideo());
    }

    @Test
    void shouldTreatAudioFlagAsSound() {
        // 阿里云万相用 audio 声明有声输出，旧键表（generate_audio / with_audio / … / sound）命中不了
        VideoBillingUsage usage = videoExtractor.extractUsage(
                Map.of("duration_seconds", 5), Map.of("audio", true)).billing();

        assertTrue(usage.sound());
    }

    @Test
    void shouldNotTreatReferenceImageAsInputVideo() {
        // 参考图走输入图片张数口径，不进「有无输入视频」这一维（robin 2026-10-26 拍板）
        VideoBillingUsage usage = videoExtractor.extractUsage(
                Map.of("duration_seconds", 5),
                Map.of("reference_images", List.of("a.png", "b.png"))).billing();

        assertFalse(usage.referenceVideo(), "参考图不得计入「有无输入视频」");
        assertEquals(2L, usage.inputImageCount(), "参考图仍按输入图片张数计量");
    }

    @Test
    void shouldTreatVideoUrlAsInputVideo() {
        // Kling 视频生视频 / 续写把输入视频放在 video / video_url，旧键表一个都不命中
        assertTrue(videoExtractor.extractUsage(Map.of("duration_seconds", 5),
                Map.of("video_url", "https://cdn.example.com/in.mp4")).billing().referenceVideo());
        assertTrue(videoExtractor.extractUsage(Map.of("duration_seconds", 5),
                Map.of("video", "https://cdn.example.com/in.mp4")).billing().referenceVideo());
    }

    @Test
    void shouldIgnoreAmbiguousReferenceKeysForInputVideo() {
        // reference / reference_url 语义不明（可能指向图片），不猜
        assertFalse(videoExtractor.extractUsage(Map.of("duration_seconds", 5),
                Map.of("reference_url", "https://cdn.example.com/a.png")).billing().referenceVideo());
    }

    @Test
    void shouldCountVideosArrayAsOutputVideoCount() {
        // Veo 把产物放在 videos 数组：条数是按条计价的乘数
        Map<String, Object> body = Map.of("videos", List.of(Map.of("url", "a.mp4"), Map.of("url", "b.mp4")));

        assertEquals(2L, videoExtractor.extractUsage(body, Map.of()).billing().outputVideoCount());
    }

    @Test
    void shouldCountSingleVideoObjectAsOne() {
        // 单条视频对象不是数组：读成 0 会让整单算成 0 元，计为 1 条
        Map<String, Object> body = Map.of("data", Map.of("url", "a.mp4"));

        assertEquals(1L, videoExtractor.extractUsage(body, Map.of()).billing().outputVideoCount());
    }

    @Test
    void shouldLeaveVendorSpecificDimensionsBlankByDefault() {
        // 画幅 / 模式 / 音色是厂商价目表档名，通用层不认识，未声明时留空（取价回落默认档）
        VideoBillingUsage usage = videoExtractor.extractUsage(
                Map.of("duration_seconds", 5), Map.of("prompt", "a cat")).billing();

        assertEquals("", usage.outputAspectRatio());
        assertEquals("", usage.outputMode());
        assertFalse(usage.voiceSpecified());
    }

    @Test
    void shouldExtractDeclaredDimensionsFromRequest() {
        // 通用键表能读到的三个维度：aspect_ratio / video_mode / voice_id
        VideoBillingUsage usage = videoExtractor.extractUsage(
                Map.of("duration_seconds", 5),
                Map.of("aspect_ratio", "16:9", "video_mode", "wan-pro", "voice_id", "male-qn-qingse")).billing();

        assertEquals("16:9", usage.outputAspectRatio());
        assertEquals("wan-pro", usage.outputMode(), "原样保存厂商写法，归一在 Calculator");
        assertTrue(usage.voiceSpecified(), "音色 id 是字符串，必须走存在性语义");
    }

    @Test
    void shouldHonorOverriddenHooksInVendorSubclass() {
        // 抽象层接线探针：厂商子类只覆写差异钩子，组装出的仍是同一份 VideoBillingUsage。
        // 若覆写不生效，下面的 7L / sound=true 会退回通用键表的结果（0 / false）而变红。
        class VendorExtractor extends AbstractVideoUsageExtractor {
            @Override
            public String code() {
                return "VIDEO-VENDOR";
            }

            @Override
            public String label() {
                return "Vendor";
            }

            @Override
            public String description() {
                return "Vendor";
            }

            @Override
            public BillingModeCalculator calculator() {
                return new VideoBillingModeCalculator();
            }

            @Override
            public Set<BillingModeEnum> billingModes() {
                return EnumSet.of(BillingModeEnum.VIDEO);
            }

            @Override
            protected long inputMillis(Map<String, Object> requestBody) {
                return 7_000L;
            }

            @Override
            protected boolean soundEnabled(Map<String, Object> requestBody) {
                return true;
            }
        }

        // 通用键表里没有任何一个键，全部靠覆写
        VideoBillingUsage usage = new VendorExtractor()
                .extractUsage(Map.of("duration_seconds", 5), Map.of("prompt", "a cat")).billing();

        assertEquals(5L, usage.outputSeconds(), "未覆写的钩子仍走通用实现");
        assertEquals(7L, usage.inputSeconds(), "覆写后应生效");
        assertTrue(usage.sound(), "覆写后应生效");
        assertFalse(usage.referenceVideo(), "未覆写时走通用键表，无参考视频");
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
        assertEquals(11L, video.outputSeconds());
        assertEquals("1080P", video.outputResolution());
    }

    @Test
    void shouldMergeUsageViewAlongsideBillingAcrossEvents() {
        // 秒数只出现在第一个事件、token 只出现在第二个：两个视图必须由同一套合并语义推出，
        // 否则会出现「计费 4 秒、展示 0 秒」的分叉
        String sse = """
                data: {"duration_seconds":4}

                data: {"usage":{"prompt_tokens":30,"completion_tokens":9,"total_tokens":39}}

                data: [DONE]
                """;

        UsageExtractItem<VideoBillingUsage> item = videoExtractor.extractUsageBody(sse, Map.of("size", "1080p"));

        assertNotNull(item.usage());
        assertEquals(4L, item.usage().output().videoSeconds());
        assertEquals(39L, item.usage().totalTokens());
        assertEquals(item.billing().outputSeconds(), item.usage().output().videoSeconds());
        assertEquals(item.billing().tokenUsage().totalTokens(), item.usage().totalTokens());
    }

    // ---------- 空响应兜底 ----------

    @Test
    void shouldReturnEmptyUsageForBlankBody() {
        assertEquals(ImageBillingUsage.empty(), imageExtractor.extractUsageBody("  ", Map.of()).billing());
        assertEquals(AudioBillingUsage.empty(), audioExtractor.extractUsageBody(null, Map.of()).billing());
        assertEquals(VideoBillingUsage.empty(), videoExtractor.extractUsageBody("not json", Map.of()).billing());
    }
}
