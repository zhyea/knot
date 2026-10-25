package org.chobit.knot.adapter.support.aws;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.support.PayloadMapSupport;
import org.chobit.knot.adapter.support.PayloadTextSupport;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.common.util.MapNumberUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AWS Bedrock Converse 的共享适配逻辑：{@code AwsBedrock*ToConverseUpstreamAdapter} 四个适配器
 * （按入协议一适配器一协议拆分）共用。
 *
 * <p>收口两类事实：① <b>Converse 目标形状构造</b>——{@link #buildConverseBody} 把归一后的
 * OpenAI chat 形状请求折成 Converse 请求；② <b>响应侧 converse→OpenAI 转换</b>——
 * {@link #handleConverseResponse} 含 usage 的 camelCase→snake_case 换算（计费侧读取的输入形状
 * 由此定义，两侧同源）。</p>
 *
 * <p>各适配器只负责把各自协议的入形状归一成 OpenAI chat 形状，协议路径（模型 ID 的 {@code :}
 * 编码成 {@code %3A}）与流式声明（EventStream 二进制帧，P1 恒整包）亦在此收口。</p>
 *
 * <p>⚠ 通用读写原语（{@code cast} / {@code nested} / {@code firstPresent} / {@code stringOf} /
 * {@code putLong}）归 {@code adapter.support.PayloadMapSupport}，跨协议 content 折文本归
 * {@code adapter.support.PayloadTextSupport}——通用能力不住本类（判例：上述辅助曾作为私有
 * 方法住在这里，2026-10-25 拆出）。</p>
 */
public final class AwsBedrockConverseSupport {

    private AwsBedrockConverseSupport() {
    }

    /** Converse 路径：模型 ID 里的 {@code :} 必须做 RFC3986 编码（签名覆盖的路径与实际发出必须同一个串）。 */
    private static final String PATH_PREFIX = "/model/";
    private static final String PATH_SUFFIX = "/converse";

    private static final String SYSTEM = "system";
    private static final String INFERENCE_CONFIG = "inferenceConfig";
    private static final String MAX_TOKENS = "maxTokens";
    private static final String TEMPERATURE = "temperature";
    private static final String TOP_P = "topP";
    private static final String STOP_SEQUENCES = "stopSequences";
    private static final String ADDITIONAL_FIELDS = "additionalModelRequestFields";
    private static final String TEXT = "text";
    private static final String OUTPUT = "output";
    private static final String MESSAGE = "message";
    private static final String CONTENT = AiPayloadFields.CONTENT;
    private static final String STOP_REASON = "stopReason";
    private static final String USAGE = AiPayloadFields.USAGE;

    /** Converse usage 的 camelCase 字段名（与 OpenAI / Anthropic 的 snake_case 不同）。 */
    private static final String INPUT_TOKENS = "inputTokens";
    private static final String OUTPUT_TOKENS = "outputTokens";
    private static final String TOTAL_TOKENS = "totalTokens";
    private static final String CACHE_READ = "cacheReadInputTokens";
    private static final String CACHE_WRITE = "cacheWriteInputTokens";

    private static final int DEFAULT_MAX_TOKENS = 1024;

    private static final Map<String, String> FINISH_REASONS = Map.of(
            "end_turn", "stop",
            "stop_sequence", "stop",
            "max_tokens", "length",
            "tool_use", "tool_calls",
            "content_filtered", "content_filter",
            "guardrail_intervened", "content_filter"
    );

    /**
     * Converse 协议路径：绑定级 {@code api_path} 优先，缺省按 {@code /model/{模型ID}/converse} 推导。
     */
    public static String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && StringUtils.isNotBlank(context.binding().apiPath())) {
            return StringUtils.trim(context.binding().apiPath());
        }
        String modelId = context.model() == null ? null : context.model().upstreamModel();
        if (StringUtils.isBlank(modelId)) {
            return defaultPath;
        }
        return PATH_PREFIX + AwsSigV4Support.uriEncode(StringUtils.trim(modelId)) + PATH_SUFFIX;
    }

    /**
     * 归一后的 OpenAI chat 形状请求 → Converse 请求体。
     *
     * <p>{@code maxTokens} 取 {@code max_completion_tokens} / {@code max_tokens} /
     * {@code max_output_tokens} 任一（缺省 1024）；{@code stop} 取 {@code stop_sequences} /
     * {@code stop} 任一——别名吸收在本方法，四个适配器无需各自处理。</p>
     */
    public static Map<String, Object> buildConverseBody(Map<String, Object> source) {
        List<Map<String, Object>> systemBlocks = new ArrayList<>();
        Object system = source.get(SYSTEM);
        if (system != null) {
            systemBlocks.add(Map.of(TEXT, PayloadMapSupport.stringOf(system)));
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        Object rawMessages = source.get(AiPayloadFields.MESSAGES);
        if (rawMessages instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> message = PayloadMapSupport.cast(raw);
                Object role = message.get(AiPayloadFields.ROLE);
                if (AiPayloadFields.SYSTEM.equals(role)) {
                    systemBlocks.add(Map.of(TEXT, PayloadTextSupport.textOf(message.get(CONTENT))));
                    continue;
                }
                Map<String, Object> converted = new LinkedHashMap<>();
                converted.put(AiPayloadFields.ROLE, role == null ? AiPayloadFields.USER : role);
                converted.put(CONTENT, List.of(Map.of(TEXT, PayloadTextSupport.textOf(message.get(CONTENT)))));
                messages.add(converted);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (!systemBlocks.isEmpty()) {
            body.put(SYSTEM, List.copyOf(systemBlocks));
        }
        body.put(AiPayloadFields.MESSAGES, messages);
        body.put(INFERENCE_CONFIG, inferenceConfig(source));

        Object stop = PayloadMapSupport.firstPresent(source, AiPayloadFields.STOP_SEQUENCES, AiPayloadFields.STOP);
        if (stop != null) {
            body.put(STOP_SEQUENCES, stop instanceof List<?> list ? List.copyOf(list) : List.of(PayloadMapSupport.stringOf(stop)));
        }
        Object topK = source.get("top_k");
        if (topK != null) {
            body.put(ADDITIONAL_FIELDS, Map.of("top_k", topK));
        }
        return body;
    }

    /**
     * Converse → OpenAI chat.completions 形状（响应适配）。
     *
     * <p>usage 一并转成 snake_case：回给客户端的与计费侧读取的是同一份产物。
     * <b>待实测（R2）</b>：Converse 的 {@code inputTokens} 是否已含缓存读写——本实现按
     * 「不含」处理（原样上报，不加回输入侧），真实环境实测后再定。</p>
     *
     * <p>非 Converse 响应（无 {@code output}，如错误体）原样返回，让上层按既有路径处理。</p>
     */
    public static String handleConverseResponse(String responseBody, UpstreamRequestContext context) {
        if (StringUtils.isBlank(responseBody)) {
            return responseBody;
        }
        Map<String, Object> converse = JsonKit.fromJson(responseBody, new TypeReference<>() {
        });
        if (converse == null || !converse.containsKey(OUTPUT)) {
            return responseBody;
        }
        Map<String, Object> message = PayloadMapSupport.nested(converse, OUTPUT, MESSAGE);
        String text = message == null ? "" : PayloadTextSupport.textOf(message.get(CONTENT));

        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put(AiPayloadFields.ROLE, "assistant");
        assistant.put(CONTENT, text);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put(MESSAGE, assistant);
        choice.put("finish_reason", finishReason(converse.get(STOP_REASON)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "chatcmpl-" + UUID.randomUUID());
        result.put("object", "chat.completion");
        result.put("created", Instant.now().getEpochSecond());
        result.put(AiPayloadFields.MODEL, context.model() == null ? null : context.model().upstreamModel());
        result.put("choices", List.of(choice));
        Map<String, Object> usage = toOpenAiUsage(converse.get(USAGE));
        if (usage != null) {
            result.put(USAGE, usage);
        }
        String json = JsonKit.toJson(result);
        return json == null ? responseBody : json;
    }

    private static Map<String, Object> inferenceConfig(Map<String, Object> source) {
        Map<String, Object> config = new LinkedHashMap<>();
        Object maxTokens = PayloadMapSupport.firstPresent(source,
                AiPayloadFields.MAX_COMPLETION_TOKENS, AiPayloadFields.MAX_TOKENS, AiPayloadFields.MAX_OUTPUT_TOKENS);
        config.put(MAX_TOKENS, maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens);
        Object temperature = source.get(TEMPERATURE);
        if (temperature != null) {
            config.put(TEMPERATURE, temperature);
        }
        Object topP = source.get("top_p");
        if (topP != null) {
            config.put(TOP_P, topP);
        }
        return config;
    }

    /**
     * Converse usage（camelCase）→ OpenAI 口径 usage（snake_case）。
     *
     * <p>回给客户端的响应必须是 OpenAI 形状，否则下游按 {@code prompt_tokens} 读不到用量；
     * 整包路径上计费侧用量提取同样以本方法的产物为输入，两侧因此不存在第二套口径。</p>
     */
    private static Map<String, Object> toOpenAiUsage(Object usageNode) {
        if (!(usageNode instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> source = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) {
                source.put(String.valueOf(key), value);
            }
        });
        Map<String, Object> usage = new LinkedHashMap<>();
        PayloadMapSupport.putLong(usage, AiPayloadFields.PROMPT_TOKENS, MapNumberUtils.firstLong(source, INPUT_TOKENS));
        PayloadMapSupport.putLong(usage, AiPayloadFields.COMPLETION_TOKENS, MapNumberUtils.firstLong(source, OUTPUT_TOKENS));
        long total = MapNumberUtils.firstLong(source, TOTAL_TOKENS);
        PayloadMapSupport.putLong(usage, AiPayloadFields.TOTAL_TOKENS,
                total > 0 ? total
                        : MapNumberUtils.firstLong(source, INPUT_TOKENS) + MapNumberUtils.firstLong(source, OUTPUT_TOKENS));
        PayloadMapSupport.putLong(usage, "cache_read_input_tokens", MapNumberUtils.firstLong(source, CACHE_READ));
        PayloadMapSupport.putLong(usage, "cache_write_input_tokens", MapNumberUtils.firstLong(source, CACHE_WRITE));
        return usage;
    }

    private static String finishReason(Object stopReason) {
        String raw = StringUtils.trimToEmpty(PayloadMapSupport.stringOf(stopReason));
        return FINISH_REASONS.getOrDefault(raw, "stop");
    }
}
