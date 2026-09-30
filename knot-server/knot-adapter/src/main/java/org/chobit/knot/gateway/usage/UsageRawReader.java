package org.chobit.knot.gateway.usage;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.util.MapNumberUtils;

import java.util.Map;

/**
 * 读取上游响应的「原始用量载体」，供 {@code model_usage.raw} 透传使用。
 *
 * <p>规则：
 * <ul>
 *   <li>整包响应：直接返回响应体本身；</li>
 *   <li>SSE 响应：逐事件解析，挑出携带 {@code usage} 且 token 总量最大的那个事件；
 *       全部事件都没有 {@code usage} 时返回最后一个事件（保住多模态场景的 data 数组兜底）；</li>
 *   <li>JSON 解析失败、响应体为空：返回 {@code null}。</li>
 * </ul>
 *
 * <p>本类只读原文，不做任何数值解析或单位换算。</p>
 */
public final class UsageRawReader {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";

    private UsageRawReader() {
    }

    /**
     * 从响应体（整包或 SSE）中取出信息量最大的那一层原始对象。
     */
    public static Map<String, Object> readBody(String responseBody) {
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
     */
    public static Map<String, Object> readEvent(String data) {
        if (StringUtils.isBlank(data) || DONE_MARKER.equals(StringUtils.trim(data))) {
            return null;
        }
        return readMap(StringUtils.trim(data));
    }

    /**
     * 取对象里的 {@code usage} 子对象，不存在或不是对象时返回 {@code null}。
     */
    public static Map<String, Object> readUsage(Map<String, Object> body) {
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

    private static Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }
}
