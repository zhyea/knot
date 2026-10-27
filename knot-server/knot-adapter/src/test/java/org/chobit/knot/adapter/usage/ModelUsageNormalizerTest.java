package org.chobit.knot.adapter.usage;

import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.adapter.model.usage.AudioBillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.ModelUsage;
import org.chobit.knot.adapter.usage.ModelUsage.Attributes;
import org.chobit.knot.adapter.usage.ModelUsage.Input;
import org.chobit.knot.adapter.usage.ModelUsage.InputTokens;
import org.chobit.knot.adapter.usage.ModelUsage.Output;
import org.chobit.knot.adapter.usage.ModelUsage.OutputTokens;
import org.chobit.knot.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.adapter.usage.UsageRawReader;
import org.chobit.knot.common.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelUsageNormalizer} 与 {@link ModelUsage} 的归一化行为测试。
 *
 * <p>2026-10-23 双视图一次提取后，{@code fromBilling} 是唯一投影入口：两侧总量与缓存取
 * {@code billing}，明细（reasoning / image / video token、媒体计数）从原始响应体补取。
 * 测试通过 {@code TokenBillingUsage.from(...)}（Anthropic 再加 {@code withCacheCountedInInput}）
 * 构造与生产一致的计费输入。</p>
 *
 * <p>覆盖：OpenAI 字段别名、Anthropic 缓存 5m/1h 互斥、unclassified 推算、
 * 多模态张数兜底、空 usage、以及 JSON 输出的 omitempty 语义。</p>
 *
 * <p>2026-10-27 起 {@code ModelUsage} 扩容为「用量 + 计费属性」，另覆盖：媒体事实的
 * 输入侧 / 输出条数 / {@code attributes} 投影、{@link ModelUsage#mergeMax} 的逐字段合并，
 * 以及「{@code VideoBillingUsage} 每个字段都有对外出口」的无损投影守门用例。</p>
 */
class ModelUsageNormalizerTest {

    @Test
    void shouldNormalizeOpenAiStyleUsage() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("prompt_tokens", 1000);
        raw.put("completion_tokens", 200);
        raw.put("total_tokens", 1200);
        raw.put("prompt_tokens_details", Map.of("cached_tokens", 100));
        raw.put("completion_tokens_details", Map.of("reasoning_tokens", 50));

        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                TokenBillingUsage.from(raw), Map.of("usage", raw));

        assertNotNull(usage);
        assertEquals(1200L, usage.totalTokens());
        assertEquals(900L, usage.input().tokens().text());
        assertEquals(100L, usage.input().tokens().cacheRead());
        assertEquals(0L, usage.input().tokens().unclassified());
        assertEquals(150L, usage.output().tokens().text());
        assertEquals(50L, usage.output().tokens().reasoning());
        assertEquals(0L, usage.output().tokens().unclassified());
    }

    @Test
    void shouldEnforceCacheWrite5m1hExclusivity() {
        // Anthropic 风格：总量与 5m/1h 明细并存时，明细优先，总量作废
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("input_tokens", 100);
        raw.put("output_tokens", 50);
        raw.put("cache_read_input_tokens", 10);
        raw.put("cache_creation_input_tokens", 30);
        raw.put("cache_creation", Map.of(
                "ephemeral_5m_input_tokens", 20,
                "ephemeral_1h_input_tokens", 10
        ));
        TokenBillingUsage billing = TokenBillingUsage.from(raw).withCacheCountedInInput();

        ModelUsage usage = ModelUsageNormalizer.fromBilling(billing, Map.of("usage", raw));

        assertNotNull(usage);
        assertEquals(20L, usage.input().tokens().cacheWrite5m());
        assertEquals(10L, usage.input().tokens().cacheWrite1h());
        assertEquals(0L, usage.input().tokens().cacheWrite(), "5m/1h 明细存在时 cache_write 总量必须归 0");
        // Anthropic 语义：input_tokens 不含缓存读写，输入总量 = 100 + cacheRead 10 + 5m 20 + 1h 10 = 140
        // text = 140 - (cacheRead 10 + 5m 20 + 1h 10) = 100
        assertEquals(100L, usage.input().tokens().text());
        assertEquals(0L, usage.input().tokens().unclassified());
        // 上游未报 total_tokens：两侧明细相加 = 140 + 50
        assertEquals(190L, usage.totalTokens());
    }

    @Test
    void shouldAddCacheBackToInputTotalForAnthropicStyleUsage() {
        // Anthropic 真实形态：input_tokens 不含缓存，5m/1h 独立上报
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("input_tokens", 700);
        raw.put("output_tokens", 200);
        raw.put("cache_read_input_tokens", 300);
        raw.put("cache_creation", Map.of(
                "ephemeral_5m_input_tokens", 400,
                "ephemeral_1h_input_tokens", 100
        ));
        TokenBillingUsage billing = TokenBillingUsage.from(raw).withCacheCountedInInput();

        ModelUsage usage = ModelUsageNormalizer.fromBilling(billing, Map.of("usage", raw));

        assertNotNull(usage);
        // 输入总量 = 700 + 300 + 400 + 100 = 1500；text = 1500 - (300 + 400 + 100) = 700
        assertEquals(700L, usage.input().tokens().text());
        assertEquals(300L, usage.input().tokens().cacheRead());
        assertEquals(400L, usage.input().tokens().cacheWrite5m());
        assertEquals(100L, usage.input().tokens().cacheWrite1h());
        // 与 billing 视图（走 BillingUsage 总量语义）必须同口径，否则两视图会打架
        assertEquals(1700L, usage.totalTokens());
        assertEquals(billing.inputTokens(), usage.input().tokens().total());
    }

    @Test
    void shouldPutUnexplainedResidueIntoUnclassified() {
        // OpenAI 语义：prompt_tokens 已含缓存命中，总量不再加缓存
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("prompt_tokens", 100);
        raw.put("text_tokens", 10);
        raw.put("image_tokens", 20);
        raw.put("video_tokens", 15);
        raw.put("prompt_tokens_details", Map.of("cached_tokens", 30));

        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                TokenBillingUsage.from(raw), Map.of("usage", raw));

        assertNotNull(usage);
        assertEquals(10L, usage.input().tokens().text());
        assertEquals(20L, usage.input().tokens().image());
        assertEquals(15L, usage.input().tokens().video());
        assertEquals(30L, usage.input().tokens().cacheRead());
        // 10 + 20 + 15 + 30 = 75，总量 100 → 残留 25 归入 unclassified
        assertEquals(25L, usage.input().tokens().unclassified());
    }

    @Test
    void shouldFallBackToDataArrayForImageGeneration() {
        // 图像接口：无 usage，只有 data 数组
        Map<String, Object> body = Map.of(
                "created", 1759084800,
                "data", List.of(Map.of("url", "a"), Map.of("url", "b"), Map.of("url", "c"))
        );

        ModelUsage usage = ModelUsageNormalizer.fromBilling(TokenBillingUsage.empty(), body);

        assertNotNull(usage);
        assertEquals(3L, usage.output().imageCount());
        assertEquals(0L, usage.totalTokens());
        assertEquals(0L, usage.input().tokenTotal());
    }

    @Test
    void shouldEnrichMediaCountersFromBody() {
        // usage 只有 token 维度，图片张数在响应体 data 数组上
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("prompt_tokens", 100);
        raw.put("completion_tokens", 50);
        raw.put("total_tokens", 150);
        Map<String, Object> body = new LinkedHashMap<>(raw);
        body.put("usage", raw);
        body.put("data", List.of(Map.of("b64_json", "x"), Map.of("b64_json", "y")));

        ImageBillingUsage billing = new ImageBillingUsage(TokenBillingUsage.from(raw), 2L, 0L, 0L, 0L);
        ModelUsage usage = ModelUsageNormalizer.fromBilling(billing, body);

        assertNotNull(usage);
        assertEquals(2L, usage.output().imageCount());
        assertEquals(150L, usage.totalTokens());
    }

    @Test
    void shouldFallBackToBillingUsageWhenRawMissing() {
        TokenBillingUsage billing = TokenBillingUsage.from(Map.of(
                "prompt_tokens", 100,
                "completion_tokens", 40,
                "total_tokens", 140,
                "prompt_tokens_details", Map.of("cached_tokens", 20)
        ));

        ModelUsage usage = ModelUsageNormalizer.fromBilling(billing, null);

        assertNotNull(usage);
        assertEquals(140L, usage.totalTokens());
        assertEquals(80L, usage.input().tokens().text());
        assertEquals(20L, usage.input().tokens().cacheRead());
        assertEquals(40L, usage.output().tokens().text());
    }

    @Test
    void shouldProjectMediaBillingUsageIntoOutputCounters() {
        // 媒体用量只进各自计量：IMAGE 张数 / VIDEO 秒数 / AUDIO 秒数，token 明细不参与
        ModelUsage image = ModelUsageNormalizer.fromBilling(
                new ImageBillingUsage(TokenBillingUsage.empty(), 3L, 1_048_576L, 1024L, 1024L), null);
        assertNotNull(image);
        assertEquals(3L, image.output().imageCount());
        assertEquals(0L, image.totalTokens());
        assertEquals(0L, image.input().tokenTotal());

        ModelUsage video = ModelUsageNormalizer.fromBilling(
                VideoBillingUsage.builder()
                        .outputVideoMillis(12_000L).outputResolution("720P").sound(true)
                        .build(), null);
        assertNotNull(video);
        assertEquals(12L, video.output().videoSeconds());
        assertEquals(0L, video.output().imageCount());

        ModelUsage audio = ModelUsageNormalizer.fromBilling(
                new AudioBillingUsage(TokenBillingUsage.empty(), 95L), null);
        assertNotNull(audio);
        assertEquals(95L, audio.output().audioSeconds());
    }

    @Test
    void shouldReturnNullWhenNothingToNormalize() {
        assertNull(ModelUsageNormalizer.fromBilling(TokenBillingUsage.empty(), null));
        assertNull(ModelUsageNormalizer.fromBilling(null, null));
        assertNull(ModelUsageNormalizer.mediaOnly(null));
    }

    @Test
    void shouldOmitZeroAndAbsentFieldsInJson() throws Exception {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("input_tokens", 100);
        raw.put("output_tokens", 50);

        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                TokenBillingUsage.from(raw), Map.of("usage", raw));
        String json = JsonKit.toJson(usage);
        JsonNode node = JsonKit.parse(json);

        // 0 值字段省略（omitempty），上游未报的维度不出现在输出里
        assertFalse(json.contains("image_count"));
        assertFalse(json.contains("audio_seconds"));
        assertFalse(json.contains("cache_write_5m"));
        assertFalse(json.contains("unclassified"));
        assertTrue(node.has("input"));
        assertTrue(node.has("output"));
        assertTrue(node.has("total_tokens"));
        assertEquals(150L, node.get("total_tokens").asLong());
        // 明细里只出现 text，其余 0 值被省略
        assertEquals(100L, node.get("input").get("tokens").get("text").asLong());
        assertFalse(node.get("input").get("tokens").has("image"));
        assertFalse(node.get("output").get("tokens").has("reasoning"));
    }

    // ---------- 计费事实投影：输入侧 / 条数 / 属性 ----------

    @Test
    void shouldProjectVideoInputSideFacts() {
        // 输入侧两轴都必须落：MiniMax 按「输出秒 + 输入秒 + 输入图张」多分量计价
        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                VideoBillingUsage.builder()
                        .outputVideoMillis(10_000L)
                        .inputVideoMillis(5_100L)
                        .inputImageCount(2L)
                        .build(), null);

        assertNotNull(usage);
        assertEquals(6L, usage.input().videoSeconds(), "输入毫秒向上取整到秒，与计费同口径");
        assertEquals(2L, usage.input().imageCount());
        assertEquals(10L, usage.output().videoSeconds());
    }

    @Test
    void shouldProjectVideoOutputCount() {
        // Veo 按条计价：条数没有出口就等于对外用量无法复算
        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                VideoBillingUsage.builder().outputVideoMillis(8_000L).outputVideoCount(3L).build(), null);

        assertNotNull(usage);
        assertEquals(3L, usage.output().videoCount());
    }

    @Test
    void shouldProjectVideoBillingAttributes() {
        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                VideoBillingUsage.builder()
                        .outputVideoMillis(5_000L)
                        .outputResolution("768P")
                        .outputAspectRatio("16:9")
                        .outputMode("MOTION_CONTROL")
                        .sound(true)
                        .voiceSpecified(true)
                        .referenceVideo(true)
                        .build(), null);

        assertNotNull(usage);
        Attributes attributes = usage.attributes();
        assertNotNull(attributes);
        assertEquals("768P", attributes.resolution());
        assertEquals("16:9", attributes.aspectRatio());
        assertEquals("MOTION_CONTROL", attributes.mode());
        assertTrue(attributes.sound());
        assertTrue(attributes.voiceSpecified());
        assertTrue(attributes.referenceVideo());
    }

    @Test
    void shouldProjectImagePixelsIntoAttributes() {
        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                new ImageBillingUsage(TokenBillingUsage.empty(), 1L, 1_048_576L, 1024L, 1024L), null);

        assertNotNull(usage);
        Attributes attributes = usage.attributes();
        assertNotNull(attributes);
        assertEquals(1024L, attributes.width());
        assertEquals(1024L, attributes.height());
        assertEquals(1_048_576L, attributes.pixels());
    }

    @Test
    void shouldProjectEveryVideoBillingFactIntoModelUsage() {
        // 无损投影守门：VideoBillingUsage 的 11 个字段逐个都要在 ModelUsage 里找到出口，
        // 否则「加计费事实忘了改对外用量」会静默丢量，下游无法复算价格
        TokenBillingUsage tokens = TokenBillingUsage.from(Map.of(
                "prompt_tokens", 100, "completion_tokens", 50, "total_tokens", 150));
        VideoBillingUsage billing = VideoBillingUsage.builder()
                .tokens(tokens)
                .outputVideoMillis(12_300L)
                .outputResolution("1080P")
                .outputAspectRatio("9:16")
                .outputMode("wan-pro")
                .outputVideoCount(2L)
                .inputVideoMillis(4_000L)
                .inputImageCount(1L)
                .sound(true)
                .voiceSpecified(true)
                .referenceVideo(true)
                .build();

        ModelUsage usage = ModelUsageNormalizer.fromBilling(billing, null);

        assertNotNull(usage);
        // 1 tokens
        assertEquals(billing.tokenUsage().inputTokens(), usage.input().tokens().total());
        assertEquals(billing.tokenUsage().outputTokens(), usage.output().tokens().total());
        assertEquals(150L, usage.totalTokens());
        // 2-6 输出量与档位
        assertEquals(billing.outputSeconds(), usage.output().videoSeconds());
        assertEquals(billing.outputVideoCount(), usage.output().videoCount());
        assertEquals(billing.outputResolution(), usage.attributes().resolution());
        assertEquals(billing.outputAspectRatio(), usage.attributes().aspectRatio());
        assertEquals(billing.outputMode(), usage.attributes().mode());
        // 7-8 输入量
        assertEquals(billing.inputSeconds(), usage.input().videoSeconds());
        assertEquals(billing.inputImageCount(), usage.input().imageCount());
        // 9-11 生成属性
        assertEquals(billing.sound(), usage.attributes().sound());
        assertEquals(billing.voiceSpecified(), usage.attributes().voiceSpecified());
        assertEquals(billing.referenceVideo(), usage.attributes().referenceVideo());
    }

    @Test
    void shouldNotMarkVideoDataArrayAsImageCount() {
        // 视频响应的 data 装的是视频，回填进 image_count 会把条数错标成图片张数
        Map<String, Object> body = Map.of("data", List.of(Map.of("url", "v1"), Map.of("url", "v2")));

        ModelUsage usage = ModelUsageNormalizer.fromBilling(
                VideoBillingUsage.builder().outputVideoMillis(6_000L).outputVideoCount(2L).build(), body);

        assertNotNull(usage);
        assertEquals(0L, usage.output().imageCount());
        assertEquals(2L, usage.output().videoCount());
        assertEquals(6L, usage.output().videoSeconds());
    }

    // ---------- 合并语义 ----------

    @Test
    void shouldMergeUsageViewsFieldByField() {
        // 与 BillingUsage#mergeMax 同规则：token 明细取总量更大的一整份，计量取 max，属性取先非空 / 布尔取或
        ModelUsage first = new ModelUsage(
                Input.builder()
                        .tokens(new InputTokens(100L, 0L, 0L, 0L, 0L, 0L, 0L, 0L))
                        .imageCount(1L).audioSeconds(2L).videoSeconds(3L)
                        .build(),
                Output.builder()
                        .tokens(new OutputTokens(10L, 0L, 0L, 0L, 0L))
                        .imageCount(1L).audioSeconds(2L).videoSeconds(3L).videoCount(1L)
                        .build(),
                Attributes.builder().resolution("720P").width(100L).build(),
                110L);
        ModelUsage second = new ModelUsage(
                Input.builder()
                        .tokens(new InputTokens(300L, 0L, 0L, 0L, 0L, 0L, 0L, 0L))
                        .imageCount(4L).audioSeconds(5L).videoSeconds(6L)
                        .build(),
                Output.builder()
                        .tokens(new OutputTokens(40L, 0L, 0L, 0L, 0L))
                        .imageCount(4L).audioSeconds(5L).videoSeconds(6L).videoCount(2L)
                        .build(),
                Attributes.builder()
                        .aspectRatio("16:9").mode("wan-pro")
                        .height(200L).pixels(20_000L)
                        .sound(true).voiceSpecified(true).referenceVideo(true)
                        .build(),
                340L);

        ModelUsage merged = first.mergeMax(second);

        assertEquals(300L, merged.input().tokens().total(), "取总量更大的一整份，不跨事件按字段拼明细");
        assertEquals(4L, merged.input().imageCount());
        assertEquals(5L, merged.input().audioSeconds());
        assertEquals(6L, merged.input().videoSeconds());
        assertEquals(40L, merged.output().tokens().total());
        assertEquals(4L, merged.output().imageCount());
        assertEquals(6L, merged.output().videoSeconds());
        assertEquals(2L, merged.output().videoCount());
        assertEquals("720P", merged.attributes().resolution());
        assertEquals("16:9", merged.attributes().aspectRatio());
        assertEquals("wan-pro", merged.attributes().mode());
        assertEquals(100L, merged.attributes().width());
        assertEquals(200L, merged.attributes().height());
        assertEquals(20_000L, merged.attributes().pixels());
        assertTrue(merged.attributes().sound());
        assertTrue(merged.attributes().voiceSpecified());
        assertTrue(merged.attributes().referenceVideo());
        assertEquals(340L, merged.totalTokens());
    }

    @Test
    void shouldKeepOtherSideWhenMergingWithNullOrMissingAttributes() {
        ModelUsage usage = ModelUsage.of(
                Input.of(new InputTokens(10L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)),
                Output.of(new OutputTokens(5L, 0L, 0L, 0L, 0L)));

        assertSame(usage, usage.mergeMax(null));
        assertNull(usage.attributes(), "纯 token 用量没有可分档属性，整段省略");

        ModelUsage withAttributes = new ModelUsage(usage.input(), usage.output(),
                Attributes.builder().resolution("4K").build(), usage.totalTokens());
        assertEquals("4K", usage.mergeMax(withAttributes).attributes().resolution());
        assertEquals("4K", withAttributes.mergeMax(usage).attributes().resolution());
    }

    @Test
    void shouldOmitAttributesSectionWhenAbsent() throws Exception {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("input_tokens", 100);
        raw.put("output_tokens", 50);
        String tokenJson = JsonKit.toJson(ModelUsageNormalizer.fromBilling(
                TokenBillingUsage.from(raw), Map.of("usage", raw)));
        assertFalse(tokenJson.contains("attributes"));
        assertFalse(tokenJson.contains("video_count"));

        String videoJson = JsonKit.toJson(ModelUsageNormalizer.fromBilling(
                VideoBillingUsage.builder().outputVideoMillis(5_000L).outputResolution("720P").build(), null));
        JsonNode attributes = JsonKit.parse(videoJson).get("attributes");
        assertNotNull(attributes);
        assertEquals("720P", attributes.get("resolution").asText());
        // 属性段内部同样按 NON_DEFAULT 省略：未声明的档位不出现，下游据此判定「未按该维度分档」
        assertFalse(attributes.has("aspect_ratio"));
        assertFalse(attributes.has("mode"));
        assertFalse(attributes.has("sound"));
    }

    @Test
    void shouldReadRawBodyFromEventStream() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"content":"hi"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}

                data: [DONE]
                """;
        Map<String, Object> body = UsageRawReader.readBody(sse);
        Map<String, Object> rawUsage = UsageRawReader.readUsage(body);

        assertNotNull(rawUsage);
        assertEquals(10, ((Number) rawUsage.get("prompt_tokens")).intValue());
        assertEquals(15, ((Number) rawUsage.get("total_tokens")).intValue());
    }

    @Test
    void shouldReadRawBodyFromBufferedResponse() throws Exception {
        Map<String, Object> body = UsageRawReader.readBody("{\"id\":\"1\",\"usage\":{\"input_tokens\":7}}");
        Map<String, Object> rawUsage = UsageRawReader.readUsage(body);

        assertNotNull(rawUsage);
        assertEquals(7, ((Number) rawUsage.get("input_tokens")).intValue());
    }
}
