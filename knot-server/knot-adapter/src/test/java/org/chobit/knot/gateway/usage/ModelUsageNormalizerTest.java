package org.chobit.knot.gateway.usage;

import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.usage.ModelUsage;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModelUsageNormalizer} 与 {@link ModelUsage} 的归一化行为测试。
 *
 * <p>覆盖：OpenAI 字段别名、Anthropic 缓存 5m/1h 互斥、unclassified 推算、
 * 多模态张数兜底、空 usage、以及 JSON 输出的 omitempty 语义。</p>
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

        ModelUsage usage = ModelUsageNormalizer.fromRaw(raw);

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

        ModelUsage usage = ModelUsageNormalizer.fromRaw(raw);

        assertNotNull(usage);
        assertEquals(20L, usage.input().tokens().cacheWrite5m());
        assertEquals(10L, usage.input().tokens().cacheWrite1h());
        assertEquals(0L, usage.input().tokens().cacheWrite(), "5m/1h 明细存在时 cache_write 总量必须归 0");
        // text = 100 - (cacheRead 10 + 5m 20 + 1h 10)
        assertEquals(60L, usage.input().tokens().text());
        assertEquals(0L, usage.input().tokens().unclassified());
        // 上游未报 total_tokens：两侧明细相加 = 100 + 50
        assertEquals(150L, usage.totalTokens());
    }

    @Test
    void shouldPutUnexplainedResidueIntoUnclassified() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("input_tokens", 100);
        raw.put("text_tokens", 10);
        raw.put("image_tokens", 20);
        raw.put("video_tokens", 15);
        raw.put("prompt_tokens_details", Map.of("cached_tokens", 30));

        ModelUsage usage = ModelUsageNormalizer.fromRaw(raw);

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

        ModelUsage usage = ModelUsageNormalizer.fromSource(null, body, BillingUsage.empty());

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

        ModelUsage usage = ModelUsageNormalizer.fromSource(raw, body, null);

        assertNotNull(usage);
        assertEquals(2L, usage.output().imageCount());
        assertEquals(150L, usage.totalTokens());
    }

    @Test
    void shouldFallBackToBillingUsageWhenRawMissing() {
        BillingUsage billing = BillingUsage.from(Map.of(
                "prompt_tokens", 100,
                "completion_tokens", 40,
                "total_tokens", 140,
                "prompt_tokens_details", Map.of("cached_tokens", 20)
        ));

        ModelUsage usage = ModelUsageNormalizer.fromSource(null, null, billing);

        assertNotNull(usage);
        assertEquals(140L, usage.totalTokens());
        assertEquals(80L, usage.input().tokens().text());
        assertEquals(20L, usage.input().tokens().cacheRead());
        assertEquals(40L, usage.output().tokens().text());
    }

    @Test
    void shouldReturnNullWhenNothingToNormalize() {
        assertNull(ModelUsageNormalizer.fromSource(null, null, BillingUsage.empty()));
        assertNull(ModelUsageNormalizer.fromSource(Map.of(), null, null));
        assertNull(ModelUsageNormalizer.fromRaw(null));
    }

    @Test
    void shouldOmitZeroAndAbsentFieldsInJson() throws Exception {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("input_tokens", 100);
        raw.put("output_tokens", 50);

        ModelUsage usage = ModelUsageNormalizer.fromRaw(raw);
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

    @Test
    void shouldReadRawBodyFromEventStream() {
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
    void shouldReadRawBodyFromBufferedResponse() {
        Map<String, Object> body = UsageRawReader.readBody("{\"id\":\"1\",\"usage\":{\"input_tokens\":7}}");
        Map<String, Object> rawUsage = UsageRawReader.readUsage(body);

        assertNotNull(rawUsage);
        assertEquals(7, ((Number) rawUsage.get("input_tokens")).intValue());
    }
}
