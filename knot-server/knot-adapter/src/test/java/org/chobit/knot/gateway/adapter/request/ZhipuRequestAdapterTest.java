package org.chobit.knot.gateway.adapter.request;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智谱 Adapter 的回归测试（清单 §2.1）。
 *
 * <p>盯三件事：① 聊天协议透传/转换不原地改写用户请求；② 图片生成属于供应商专用重建路径，
 * 只搬目标协议明确接受的字段，并做别名映射；③ 未知字段在重建路径上会被有意丢弃，
 * 这里把「哪些字段会丢」固化下来，避免以后被当成 bug 或意外放大成全局裁剪。
 */
class ZhipuRequestAdapterTest {

    private final ZhipuRequestAdapter adapter = new ZhipuRequestAdapter();

    @Test
    void shouldPassThroughChatCompletionsProtocol() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, "user", "content", "hi")));
        source.put("vendor_only_field", "keep-me");

        UpstreamRequestContext ctx = context(ModelApiProtocolEnum.CHAT_COMPLETIONS, source, null);
        Object result = adapter.buildRequestBody(ctx);

        assertSame(ctx.requestBody(), result, "CHAT_COMPLETIONS 无需转换，应原样返回");
        assertEquals("keep-me", asMap(result).get("vendor_only_field"));
    }

    @Test
    void shouldNotMutateOriginalRequestBodyDuringMessagesConversion() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.SYSTEM, "你是一个助手");
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "你好"))));
        source.put(AiPayloadFields.STOP_SEQUENCES, List.of("###"));
        source.put("top_k", 40);

        adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source, null));

        assertTrue(source.containsKey(AiPayloadFields.SYSTEM), "原始请求仍应保留 system");
        assertTrue(source.containsKey(AiPayloadFields.STOP_SEQUENCES), "原始请求仍应保留 stop_sequences");
        assertFalse(source.containsKey(AiPayloadFields.STOP), "原始请求不应被写入 stop");
    }

    @Test
    void shouldMoveOnlyRequiredFieldsDuringMessagesConversion() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.SYSTEM, "你是一个助手");
        source.put(AiPayloadFields.MESSAGES, new ArrayList<>(List.of(
                Map.of(AiPayloadFields.ROLE, "user", AiPayloadFields.CONTENT, "你好"))));
        source.put(AiPayloadFields.STOP_SEQUENCES, List.of("###"));
        source.put("top_k", 40);
        source.put("temperature", 0.7);

        Map<String, Object> result = asMap(
                adapter.buildRequestBody(context(ModelApiProtocolEnum.MESSAGES, source, null)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) result.get(AiPayloadFields.MESSAGES);
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get(AiPayloadFields.ROLE));
        assertEquals(List.of("###"), result.get(AiPayloadFields.STOP));
        assertFalse(result.containsKey(AiPayloadFields.STOP_SEQUENCES));
        assertFalse(result.containsKey("top_k"));
        assertEquals(0.7, result.get("temperature"), "非协议冲突字段保留");
    }

    @Test
    void shouldRebuildVendorSpecificImageBody() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.PROMPT, "一只猫");
        source.put("size", "1024x1024");
        source.put("quality", "standard");
        source.put("watermark_enabled", true);
        source.put("user_id", "u-1");
        source.put("vendor_only_field", "dropped-on-purpose");

        Map<String, Object> result = asMap(adapter.buildRequestBody(
                context(ModelApiProtocolEnum.IMAGE_GENERATIONS, source, model("glm-image"))));

        // 供应商专用重建：model / prompt 必填，只搬目标协议接受的字段
        assertEquals("glm-image", result.get(AiPayloadFields.MODEL));
        assertEquals("一只猫", result.get(AiPayloadFields.PROMPT));
        assertEquals("1024x1024", result.get("size"));
        assertEquals("standard", result.get("quality"));
        // 别名归一：无论源里写主名还是别名，输出都用主名 watermark_enabled / user_id
        assertEquals(true, result.get("watermark_enabled"));
        assertEquals("u-1", result.get("user_id"));
        assertFalse(result.containsKey("watermark"));
        assertFalse(result.containsKey("user"));
        // 该路径是有意的字段裁剪，未知字段不保留（与透传路径的语义不同，此处固化该契约）
        assertFalse(result.containsKey("vendor_only_field"));
    }

    @Test
    void shouldNormalizeAliasSpellingToCanonicalName() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put(AiPayloadFields.PROMPT, "一只猫");
        source.put("watermark", true);
        source.put("user", "u-2");

        Map<String, Object> result = asMap(adapter.buildRequestBody(
                context(ModelApiProtocolEnum.IMAGE_GENERATIONS, source, model("glm-image"))));

        // 源里只给了别名，输出仍归一到主名
        assertEquals(true, result.get("watermark_enabled"));
        assertEquals("u-2", result.get("user_id"));
        assertFalse(result.containsKey("watermark"));
        assertFalse(result.containsKey("user"));
    }

    private ModelEntity model(String modelCode) {
        ModelEntity model = new ModelEntity();
        model.setModelCode(modelCode);
        return model;
    }

    private UpstreamRequestContext context(ModelApiProtocolEnum protocol, Map<String, Object> body, ModelEntity model) {
        return new UpstreamRequestContext(protocol, body, null, model, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
