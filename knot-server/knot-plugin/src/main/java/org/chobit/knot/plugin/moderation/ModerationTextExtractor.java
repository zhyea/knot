package org.chobit.knot.plugin.moderation;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.util.JsonKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 待检测文本抽取：只取文本字段，媒体内容与结构化字段不参与匹配。
 *
 * <p>请求侧按协议取 {@code messages[].content} / {@code prompt} / {@code input} /
 * {@code instructions} / {@code system}；响应侧只递归取键名为 {@code content} / {@code text} 的字符串，
 * 避免把模型名、角色名、id 等结构字段当成正文造成误杀。</p>
 */
public final class ModerationTextExtractor {

    private static final String TEXT = "text";

    private ModerationTextExtractor() {
    }

    /**
     * 抽取请求体中的待检测文本。
     */
    public static List<String> fromRequest(Map<String, Object> body) {
        List<String> texts = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return texts;
        }
        collectMessages(body.get(AiPayloadFields.MESSAGES), texts);
        addValue(body.get(AiPayloadFields.PROMPT), texts);
        addValue(body.get(AiPayloadFields.INPUT), texts);
        addValue(body.get(AiPayloadFields.INSTRUCTIONS), texts);
        addValue(body.get(AiPayloadFields.SYSTEM), texts);
        return texts;
    }

    /**
     * 抽取整包响应体中的待检测文本；解析失败时返回空列表（不因解析问题拦截业务）。
     */
    public static List<String> fromResponse(String responseBody) {
        List<String> texts = new ArrayList<>();
        if (responseBody == null || responseBody.isBlank()) {
            return texts;
        }
        Map<String, Object> body = JsonKit.fromJson(responseBody, new TypeReference<>() {
        });
        collectTextFields(body, texts);
        return texts;
    }

    /**
     * 插件视角的响应体抽取：{@code GatewayPluginContext#responseBody()} 是 {@code Object}——
     * 整包场景既可能是上游原始 JSON 字符串，也可能是已注入 {@code knot_extend} 的 Map。
     *
     * <p>流式场景拿到的是 {@code StreamingResponseBody}，此处直接返回空列表（M2 增量检测再覆盖），
     * 避免对非 JSON 对象做无意义递归。</p>
     */
    public static List<String> fromResponseObject(Object responseBody) {
        if (responseBody instanceof String text) {
            return fromResponse(text);
        }
        List<String> texts = new ArrayList<>();
        if (responseBody instanceof Map<?, ?> || responseBody instanceof List<?>) {
            collectTextFields(responseBody, texts);
        }
        return texts;
    }

    private static void collectMessages(Object messages, List<String> texts) {
        if (!(messages instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> message) {
                addValue(message.get(AiPayloadFields.CONTENT), texts);
            }
        }
    }

    /**
     * content 可能是字符串，也可能是多模态 parts 数组（取其中的 text 片段）。
     */
    private static void addValue(Object value, List<String> texts) {
        if (value instanceof String s) {
            if (!s.isBlank()) {
                texts.add(s);
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> part) {
                    addValue(part.get(TEXT), texts);
                } else if (item instanceof String s && !s.isBlank()) {
                    texts.add(s);
                }
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            addValue(map.get(TEXT), texts);
        }
    }

    private static void collectTextFields(Object node, List<String> texts) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (isTextKey(entry.getKey()) && value instanceof String s && !s.isBlank()) {
                    texts.add(s);
                    continue;
                }
                collectTextFields(value, texts);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                collectTextFields(item, texts);
            }
        }
    }

    private static boolean isTextKey(Object key) {
        return key instanceof String field
                && (AiPayloadFields.CONTENT.equals(field) || TEXT.equals(field));
    }
}
