package org.chobit.knot.adapter.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.common.util.MapNumberUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读取上游响应的「原始用量载体」，作为归一化输入，不对外透传。
 * 整包响应直接返回响应体本身；SSE 逐事件解析，取携带 {@code usage} 且 token 总量最大的事件，全部没有 {@code usage} 时返回最后一个事件。
 * usage 的落位随协议不同：顶层 {@code usage}（ChatCompletions 与 Anthropic 的 {@code message_delta}）、
 * {@code message.usage}（Anthropic 原生流式的 {@code message_start}）、{@code response.usage}（Responses）。
 * JSON 解析失败抛 {@code JsonProcessingException}（不静默降级为 {@code null}）；响应体为空返回 {@code null}。
 * 本类只读原文，不做任何数值解析或单位换算。
 */
public final class UsageRawReader {

    private static final String DATA_PREFIX = "data:";

    /** Anthropic 原生流式把首个事件的用量放在 {@code message.usage} 里 */
    private static final String MESSAGE_KEY = "message";
    private static final String DONE_MARKER = "[DONE]";

    private UsageRawReader() {
    }

    /**
     * 从响应体（整包或 SSE）中取出信息量最大的那一层原始对象。
     *
     * @throws JsonProcessingException 响应体不是合法 JSON
     */
    public static Map<String, Object> readBody(String responseBody) throws JsonProcessingException {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        if (!isEventStream(responseBody)) {
            return readMap(responseBody);
        }
        Map<String, Object> fallback = null;
        Map<String, Object> best = null;
        long bestScore = 0L;
        for (String line : responseBody.split("\\R")) {
            String trimmed = StringUtils.trim(line);
            if (!StringUtils.startsWith(trimmed, DATA_PREFIX)) {
                continue;
            }
            String data = StringUtils.trim(trimmed.substring(DATA_PREFIX.length()));
            if (StringUtils.isEmpty(data) || DONE_MARKER.equals(data)) {
                continue;
            }
            Map<String, Object> event = readMap(data);
            if (event == null || event.isEmpty()) {
                continue;
            }
            fallback = event;
            long score = score(event);
            if (score > bestScore) {
                bestScore = score;
                best = event;
            }
        }
        return best != null ? best : fallback;
    }

    /**
     * 从 SSE 单个 {@code data:} 载荷中取出原始对象。
     *
     * @throws JsonProcessingException 载荷不是合法 JSON
     */
    public static Map<String, Object> readEvent(String data) throws JsonProcessingException {
        if (StringUtils.isBlank(data) || DONE_MARKER.equals(StringUtils.trim(data))) {
            return null;
        }
        return readMap(StringUtils.trim(data));
    }

    /**
     * 取对象里的 {@code usage} 子对象，不存在或不是对象时返回 {@code null}。
     * 顶层没有 {@code usage} 时按协议下钻：先 {@code message.usage}（Anthropic 原生流式的首个事件），
     * 再 {@code response.usage}（Responses）。
     */
    public static Map<String, Object> readUsage(Map<String, Object> body) {
        Map<String, Object> usage = usageOf(body);
        if (usage != null) {
            return usage;
        }
        if (body == null) {
            return null;
        }
        usage = nestedUsageOf(body, MESSAGE_KEY);
        return usage != null ? usage : nestedUsageOf(body, AiPayloadFields.RESPONSE);
    }

    /** 下钻一层取 {@code usage}：外层不是对象或内层没有 {@code usage} 时返回 {@code null}。 */
    private static Map<String, Object> nestedUsageOf(Map<String, Object> body, String key) {
        if (!(body.get(key) instanceof Map<?, ?> nested)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) nested;
        return usageOf(typed);
    }

    /**
     * 逐事件累计：同名顶层字段后到覆盖，前一条有而后一条没出现的字段保留。
     * 只补顶层缺项，嵌套对象（如 {@code prompt_tokens_details}）整块后到覆盖。
     */
    public static Map<String, Object> mergeUsage(Map<String, Object> previous, Map<String, Object> incoming) {
        if (previous == null || previous.isEmpty()) {
            return incoming;
        }
        if (incoming == null || incoming.isEmpty()) {
            return previous;
        }
        Map<String, Object> merged = new LinkedHashMap<>(previous);
        merged.putAll(incoming);
        return merged;
    }

    /**
     * 把用量写回它在响应体里的位置：顶层 {@code usage} 优先，其次 {@code message.usage}，再次 {@code response.usage}；
     * 三处都没有时补到顶层。返回副本，不改入参。
     */
    public static Map<String, Object> withUsage(Map<String, Object> body, Map<String, Object> usage) {
        if (body == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(body);
        if (copy.get(AiPayloadFields.USAGE) instanceof Map<?, ?>) {
            copy.put(AiPayloadFields.USAGE, usage);
            return copy;
        }
        if (replaceNestedUsage(copy, MESSAGE_KEY, usage)
                || replaceNestedUsage(copy, AiPayloadFields.RESPONSE, usage)) {
            return copy;
        }
        copy.put(AiPayloadFields.USAGE, usage);
        return copy;
    }

    /** 外层对象本来就有 {@code usage} 时才替换，避免给没有用量的层级凭空造一个。 */
    private static boolean replaceNestedUsage(Map<String, Object> body, String key, Map<String, Object> usage) {
        if (!(body.get(key) instanceof Map<?, ?> nested) || !(nested.get(AiPayloadFields.USAGE) instanceof Map<?, ?>)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) nested;
        Map<String, Object> replaced = new LinkedHashMap<>(typed);
        replaced.put(AiPayloadFields.USAGE, usage);
        body.put(key, replaced);
        return true;
    }

    private static Map<String, Object> usageOf(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        Object usage = body.get(AiPayloadFields.USAGE);
        if (!(usage instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) usage;
        return typed;
    }

    /**
     * 事件的用量信息量：有 usage 时取两侧 token 之和，其余情况为 0。
     */
    private static long score(Map<String, Object> event) {
        Map<String, Object> usage = readUsage(event);
        if (usage == null) {
            return 0L;
        }
        long inputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.INPUT_TOKENS, AiPayloadFields.PROMPT_TOKENS);
        long outputTokens = MapNumberUtils.firstLong(usage, AiPayloadFields.OUTPUT_TOKENS, AiPayloadFields.COMPLETION_TOKENS);
        return Math.max(0L, inputTokens) + Math.max(0L, outputTokens);
    }

    private static boolean isEventStream(String value) {
        return value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), DATA_PREFIX));
    }

    private static Map<String, Object> readMap(String json) throws JsonProcessingException {
        return JsonKit.fromJsonOrThrow(json, new TypeReference<>() {
        });
    }
}
