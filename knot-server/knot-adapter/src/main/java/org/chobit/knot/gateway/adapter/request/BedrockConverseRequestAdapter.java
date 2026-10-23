package org.chobit.knot.gateway.adapter.request;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.aws.AwsSigV4Support;
import org.chobit.knot.gateway.adapter.aws.BedrockConverseUsageSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AWS Bedrock Converse 请求适配器：把 OpenAI 形状的请求转成 Converse，再把 Converse 响应转回 OpenAI 形状。
 *
 * <p><b>只服务 {@code bedrock-runtime} 端点</b>：mantle 端点不支持 Converse（只暴露 OpenAI 兼容与
 * Anthropic Messages），mantle 模型应直接绑定既有的 {@code OPENAI_COMPATIBLE} 适配器。</p>
 *
 * <p>P1 只做文本：请求侧把 {@code messages} 折成 Converse 的 {@code content[{text}]}，
 * 响应侧把 {@code output.message.content[].text} 拼回 {@code choices[0].message.content}。</p>
 */
@Component
@Order(30)
public class BedrockConverseRequestAdapter implements UpstreamRequestAdapter {

    public static final String CODE = "BEDROCK_CONVERSE";

    /** Converse 路径：模型 ID 里的 {@code :} 必须做 RFC3986 编码（签名覆盖的路径与实际发出必须同一个串）。 */
    private static final String PATH_PREFIX = "/model/";
    private static final String PATH_SUFFIX = "/converse";

    private static final String SYSTEM = "system";
    private static final String MESSAGES = AiPayloadFields.MESSAGES;
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

    private static final int DEFAULT_MAX_TOKENS = 1024;

    private static final Map<String, String> FINISH_REASONS = Map.of(
            "end_turn", "stop",
            "stop_sequence", "stop",
            "max_tokens", "length",
            "tool_use", "tool_calls",
            "content_filtered", "content_filter",
            "guardrail_intervened", "content_filter"
    );

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "AWS Bedrock Converse";
    }

    @Override
    public Set<ModelApiProtocolEnum> protocol() {
        return Set.of(
                ModelApiProtocolEnum.CHAT_COMPLETIONS,
                ModelApiProtocolEnum.RESPONSES,
                ModelApiProtocolEnum.MESSAGES,
                ModelApiProtocolEnum.COMPLETIONS
        );
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && StringUtils.isNotBlank(context.binding().getApiPath())) {
            return StringUtils.trim(context.binding().getApiPath());
        }
        String modelId = context.model() == null ? null : context.model().getUpstreamModel();
        if (StringUtils.isBlank(modelId)) {
            return UpstreamRequestAdapter.super.resolvePath(context, defaultPath);
        }
        return PATH_PREFIX + AwsSigV4Support.uriEncode(StringUtils.trim(modelId)) + PATH_SUFFIX;
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        Map<String, Object> source = context.requestBody();
        List<Map<String, Object>> systemBlocks = new ArrayList<>();
        Object system = source.get(SYSTEM);
        if (system != null) {
            systemBlocks.add(Map.of(TEXT, stringOf(system)));
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        Object rawMessages = source.get(MESSAGES);
        if (rawMessages instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> message = cast(raw);
                Object role = message.get(AiPayloadFields.ROLE);
                if (AiPayloadFields.SYSTEM.equals(role)) {
                    systemBlocks.add(Map.of(TEXT, textOf(message.get(CONTENT))));
                    continue;
                }
                Map<String, Object> converted = new LinkedHashMap<>();
                converted.put(AiPayloadFields.ROLE, role == null ? AiPayloadFields.USER : role);
                converted.put(CONTENT, List.of(Map.of(TEXT, textOf(message.get(CONTENT)))));
                messages.add(converted);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        if (!systemBlocks.isEmpty()) {
            body.put(SYSTEM, List.copyOf(systemBlocks));
        }
        body.put(MESSAGES, messages);
        body.put(INFERENCE_CONFIG, inferenceConfig(source));

        Object stop = firstPresent(source, AiPayloadFields.STOP_SEQUENCES, AiPayloadFields.STOP);
        if (stop != null) {
            body.put(STOP_SEQUENCES, stop instanceof List<?> list ? List.copyOf(list) : List.of(stringOf(stop)));
        }
        Object topK = source.get("top_k");
        if (topK != null) {
            body.put(ADDITIONAL_FIELDS, Map.of("top_k", topK));
        }
        return body;
    }

    /**
     * Converse → OpenAI chat.completions 形状。
     *
     * <p>usage 一并转成 snake_case：回给客户端的必须是 OpenAI 形状，
     * 否则下游按 {@code prompt_tokens} 读不到用量。</p>
     */
    @Override
    public String handleResponse(String responseBody, UpstreamRequestContext context) {
        if (StringUtils.isBlank(responseBody)) {
            return responseBody;
        }
        Map<String, Object> converse = readMap(responseBody);
        if (converse == null || !converse.containsKey(OUTPUT)) {
            // 不是 Converse 响应（如错误体），原样返回让上层按既有路径处理
            return responseBody;
        }
        Map<String, Object> message = nested(converse, OUTPUT, MESSAGE);
        String text = message == null ? "" : textOf(message.get(CONTENT));

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
        result.put(AiPayloadFields.MODEL, context.model() == null ? null : context.model().getUpstreamModel());
        result.put("choices", List.of(choice));
        Map<String, Object> usage = BedrockConverseUsageSupport.toOpenAiUsage(converse.get(USAGE));
        if (usage != null) {
            result.put(USAGE, usage);
        }
        String json = JsonKit.toJson(result);
        return json == null ? responseBody : json;
    }

    /**
     * P1 不支持流式：Converse 的流式是 AWS EventStream 二进制帧而非 SSE，需要独立的解码器（P2）。
     * 返回 {@code false} 让执行器走整包路径。
     */
    @Override
    public boolean streamSupported(UpstreamRequestContext context) {
        return false;
    }

    private Map<String, Object> inferenceConfig(Map<String, Object> source) {
        Map<String, Object> config = new LinkedHashMap<>();
        Object maxTokens = firstPresent(source,
                AiPayloadFields.MAX_COMPLETION_TOKENS, AiPayloadFields.MAX_TOKENS, AiPayloadFields.MAX_OUTPUT_TOKENS);
        config.put(MAX_TOKENS, maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens);
        Object temperature = source.get("temperature");
        if (temperature != null) {
            config.put(TEMPERATURE, temperature);
        }
        Object topP = source.get("top_p");
        if (topP != null) {
            config.put(TOP_P, topP);
        }
        return config;
    }

    private static String finishReason(Object stopReason) {
        String raw = StringUtils.trimToEmpty(String.valueOf(stopReason == null ? "" : stopReason));
        return FINISH_REASONS.getOrDefault(raw, "stop");
    }

    /** 文本内容：既吃 OpenAI 的裸字符串，也吃 Anthropic / Converse 的 {@code [{text: ...}]}。 */
    private static String textOf(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String value) {
            return value;
        }
        if (content instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Object text = cast(raw).get(TEXT);
                    if (text != null) {
                        builder.append(text);
                    }
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Object firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> body, String outer, String inner) {
        Object node = body.get(outer);
        if (!(node instanceof Map<?, ?> raw)) {
            return null;
        }
        Object child = cast(raw).get(inner);
        return child instanceof Map<?, ?> map ? cast(map) : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private static Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }
}
