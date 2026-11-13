package org.chobit.knot.adapter.support.anthropic;

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
 * Anthropic Messages 响应形状的双向转换（协议级，不隶属单一供应商）；请求侧形状见
 * {@link AnthropicMessagesRequestSupport}。非目标形状（错误体 / 非 JSON / 缺 {@code choices}）原样透传。
 * usage 双向换算 {@code input_tokens/output_tokens} ↔ {@code prompt_tokens/completion_tokens}；
 * Messages→OpenAI 的缓存读写以独立键携带，反向不带缓存键。
 */
public final class AnthropicMessagesResponseSupport {

    private static final String TYPE = "type";
    private static final String MESSAGE = "message";
    private static final String ERROR = "error";
    private static final String CONTENT = AiPayloadFields.CONTENT;
    private static final String STOP_REASON = "stop_reason";
    private static final String ASSISTANT = "assistant";
    private static final String CHOICES = "choices";
    private static final String CHOICE_MESSAGE = "message";
    private static final String FINISH_REASON = "finish_reason";
    private static final String INDEX = "index";
    private static final String OBJECT = "object";
    private static final String CREATED = "created";
    private static final String CREATED_AT = "created_at";
    private static final String TEXT = "text";
    private static final String OUTPUT = "output";
    private static final String STATUS = "status";
    private static final String COMPLETED = "completed";
    private static final String ANNOTATIONS = "annotations";
    private static final String OUTPUT_TEXT = "output_text";
    private static final String CACHE_READ = "cache_read_input_tokens";
    private static final String CACHE_CREATION = "cache_creation_input_tokens";
    private static final String CACHED_TOKENS = "cached_tokens";
    private static final String INPUT_TOKENS_DETAILS = "input_tokens_details";

    private static final String CHAT_COMPLETION = "chat.completion";
    private static final String TEXT_COMPLETION = "text_completion";
    private static final String RESPONSE = "response";

    /**
     * Anthropic {@code stop_reason} → OpenAI {@code finish_reason}，只认 Anthropic 词表
     * （含 {@code pause_turn} / {@code refusal}）。
     */
    private static final Map<String, String> FINISH_REASONS = Map.of(
            "end_turn", "stop",
            "stop_sequence", "stop",
            "max_tokens", "length",
            "tool_use", "tool_calls",
            "pause_turn", "stop",
            "refusal", "content_filter"
    );

    /** OpenAI {@code finish_reason} → Anthropic {@code stop_reason}（反向）。 */
    private static final Map<String, String> STOP_REASONS = Map.of(
            "stop", "end_turn",
            "length", "max_tokens",
            "tool_calls", "tool_use",
            "content_filter", "refusal"
    );

    private AnthropicMessagesResponseSupport() {
    }

    /** Messages 响应 → OpenAI {@code chat.completion}；非 Messages 响应原样返回。 */
    public static String messagesToChatCompletion(String responseBody, UpstreamRequestContext context) {
        Map<String, Object> message = messagesResponse(responseBody);
        if (message == null) {
            return responseBody;
        }
        Map<String, Object> assistant = assistantMessage(PayloadTextSupport.textOf(message.get(CONTENT)));
        List<Map<String, Object>> toolCalls = AnthropicToolSupport.toolCallsOf(message.get(CONTENT));
        if (!toolCalls.isEmpty()) {
            assistant.put(AiPayloadFields.TOOL_CALLS, toolCalls);
        }

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put(INDEX, 0);
        choice.put(CHOICE_MESSAGE, assistant);
        choice.put(FINISH_REASON, finishReason(message.get(STOP_REASON)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "chatcmpl-" + UUID.randomUUID());
        result.put(OBJECT, CHAT_COMPLETION);
        result.put(CREATED, Instant.now().getEpochSecond());
        result.put(AiPayloadFields.MODEL, modelOf(context));
        result.put(CHOICES, List.of(choice));
        putOpenAiUsage(result, message.get(AiPayloadFields.USAGE));
        return jsonOrOriginal(result, responseBody);
    }

    /** Messages 响应 → OpenAI 旧版 {@code text_completion}；非 Messages 响应原样返回。 */
    public static String messagesToTextCompletion(String responseBody, UpstreamRequestContext context) {
        Map<String, Object> message = messagesResponse(responseBody);
        if (message == null) {
            return responseBody;
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put(TEXT, PayloadTextSupport.textOf(message.get(CONTENT)));
        choice.put(INDEX, 0);
        choice.put(FINISH_REASON, finishReason(message.get(STOP_REASON)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "cmpl-" + UUID.randomUUID());
        result.put(OBJECT, TEXT_COMPLETION);
        result.put(CREATED, Instant.now().getEpochSecond());
        result.put(AiPayloadFields.MODEL, modelOf(context));
        result.put(CHOICES, List.of(choice));
        putOpenAiUsage(result, message.get(AiPayloadFields.USAGE));
        return jsonOrOriginal(result, responseBody);
    }

    /** Messages 响应 → OpenAI Responses {@code response} 对象；非 Messages 响应原样返回。 */
    public static String messagesToResponses(String responseBody, UpstreamRequestContext context) {
        Map<String, Object> message = messagesResponse(responseBody);
        if (message == null) {
            return responseBody;
        }
        String text = PayloadTextSupport.textOf(message.get(CONTENT));
        List<Map<String, Object>> functionCalls = AnthropicToolSupport.functionCallsOf(message.get(CONTENT));

        List<Object> output = new ArrayList<>();
        // 纯工具调用轮次没有文本，此时不硬塞一个空 output_text；两者都无时仍留一条空消息，
        // 保持与「文本轮次」同构的响应骨架
        if (!text.isEmpty() || functionCalls.isEmpty()) {
            output.add(outputMessage(text));
        }
        output.addAll(functionCalls);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "resp_" + UUID.randomUUID());
        result.put(OBJECT, RESPONSE);
        result.put(CREATED_AT, Instant.now().getEpochSecond());
        result.put(AiPayloadFields.MODEL, modelOf(context));
        result.put(STATUS, COMPLETED);
        result.put(OUTPUT, output);
        putResponsesUsage(result, message.get(AiPayloadFields.USAGE));
        return jsonOrOriginal(result, responseBody);
    }

    private static Map<String, Object> outputMessage(String text) {
        Map<String, Object> outputText = new LinkedHashMap<>();
        outputText.put(TYPE, OUTPUT_TEXT);
        outputText.put(TEXT, text);
        outputText.put(ANNOTATIONS, List.of());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put(TYPE, MESSAGE);
        message.put("id", "msg_" + UUID.randomUUID());
        message.put(AiPayloadFields.ROLE, ASSISTANT);
        message.put(STATUS, COMPLETED);
        message.put(CONTENT, List.of(outputText));
        return message;
    }

    /** OpenAI {@code chat.completion} → Messages 响应；非 chat.completion 响应原样返回。 */
    public static String chatCompletionToMessages(String responseBody, UpstreamRequestContext context) {
        Map<String, Object> completion = chatCompletionResponse(responseBody);
        if (completion == null) {
            return responseBody;
        }
        Object firstChoice = ((List<?>) completion.get(CHOICES)).get(0);
        Map<String, Object> choice = PayloadMapSupport.cast((Map<?, ?>) firstChoice);
        Map<String, Object> message = choice.get(CHOICE_MESSAGE) instanceof Map<?, ?> raw
                ? PayloadMapSupport.cast(raw) : Map.of();

        String text = PayloadTextSupport.textOf(message.get(CONTENT));
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (!text.isEmpty()) {
            blocks.add(textBlock(text));
        }
        blocks.addAll(AnthropicToolSupport.toolUseBlocksOf(message.get(AiPayloadFields.TOOL_CALLS)));
        if (blocks.isEmpty()) {
            // 既无文本也无工具：保留一个空文本块，与「有文本」时同构的 Messages 骨架
            blocks.add(textBlock(""));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "msg_" + UUID.randomUUID());
        result.put(TYPE, MESSAGE);
        result.put(AiPayloadFields.ROLE, ASSISTANT);
        result.put(AiPayloadFields.MODEL, modelOf(context));
        result.put(CONTENT, blocks);
        result.put(STOP_REASON, stopReason(choice.get(FINISH_REASON)));
        Map<String, Object> usage = anthropicUsage(completion.get(AiPayloadFields.USAGE));
        if (!usage.isEmpty()) {
            result.put(AiPayloadFields.USAGE, usage);
        }
        return jsonOrOriginal(result, responseBody);
    }

    private static Map<String, Object> assistantMessage(String text) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put(AiPayloadFields.ROLE, ASSISTANT);
        assistant.put(CONTENT, text);
        return assistant;
    }

    private static Map<String, Object> textBlock(String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put(TYPE, TEXT);
        block.put(TEXT, text);
        return block;
    }

    /**
     * Messages 响应的识别：{@code type=message} 且 {@code content} 是块列表；
     * 错误体（{@code type:error}）与任何异形体判否。
     */
    private static Map<String, Object> messagesResponse(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        Map<String, Object> message = JsonKit.fromJson(responseBody, new TypeReference<>() {
        });
        if (message == null || ERROR.equals(message.get(TYPE))) {
            return null;
        }
        if (!MESSAGE.equals(message.get(TYPE)) || !(message.get(CONTENT) instanceof List<?>)) {
            return null;
        }
        return message;
    }

    /** chat.completion 响应的识别：{@code object=chat.completion} 且 choices 非空。 */
    private static Map<String, Object> chatCompletionResponse(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return null;
        }
        Map<String, Object> completion = JsonKit.fromJson(responseBody, new TypeReference<>() {
        });
        if (completion == null || !CHAT_COMPLETION.equals(completion.get(OBJECT))) {
            return null;
        }
        return completion.get(CHOICES) instanceof List<?> choices && !choices.isEmpty()
                && choices.get(0) instanceof Map<?, ?> ? completion : null;
    }

    /**
     * Messages usage → OpenAI 口径 usage：{@code input→prompt}、{@code output→completion}，
     * {@code total} 缺失时按两者之和补齐；缓存读写沿用上游键名独立携带（见类注释 R2）。
     */
    private static void putOpenAiUsage(Map<String, Object> target, Object usageNode) {
        Map<String, Object> source = usageOf(usageNode);
        if (source == null) {
            return;
        }
        long input = MapNumberUtils.firstLong(source, AiPayloadFields.INPUT_TOKENS, AiPayloadFields.PROMPT_TOKENS);
        long output = MapNumberUtils.firstLong(source, AiPayloadFields.OUTPUT_TOKENS, AiPayloadFields.COMPLETION_TOKENS);
        long total = MapNumberUtils.firstLong(source, AiPayloadFields.TOTAL_TOKENS);

        Map<String, Object> usage = new LinkedHashMap<>();
        PayloadMapSupport.putLong(usage, AiPayloadFields.PROMPT_TOKENS, input);
        PayloadMapSupport.putLong(usage, AiPayloadFields.COMPLETION_TOKENS, output);
        PayloadMapSupport.putLong(usage, AiPayloadFields.TOTAL_TOKENS, total > 0 ? total : input + output);
        putPositive(usage, source, CACHE_READ);
        putPositive(usage, source, CACHE_CREATION);
        target.put(AiPayloadFields.USAGE, usage);
    }

    /**
     * Messages usage → Responses 口径 usage：{@code input_tokens/output_tokens/total_tokens}
     * （Responses 与 Messages 的输入输出键名相同，故不换算），缓存读写挂到
     * {@code input_tokens_details}（Responses 的明细位置）。
     */
    private static void putResponsesUsage(Map<String, Object> target, Object usageNode) {
        Map<String, Object> source = usageOf(usageNode);
        if (source == null) {
            return;
        }
        long input = MapNumberUtils.firstLong(source, AiPayloadFields.INPUT_TOKENS, AiPayloadFields.PROMPT_TOKENS);
        long output = MapNumberUtils.firstLong(source, AiPayloadFields.OUTPUT_TOKENS, AiPayloadFields.COMPLETION_TOKENS);
        long total = MapNumberUtils.firstLong(source, AiPayloadFields.TOTAL_TOKENS);

        Map<String, Object> usage = new LinkedHashMap<>();
        PayloadMapSupport.putLong(usage, AiPayloadFields.INPUT_TOKENS, input);
        PayloadMapSupport.putLong(usage, AiPayloadFields.OUTPUT_TOKENS, output);
        PayloadMapSupport.putLong(usage, AiPayloadFields.TOTAL_TOKENS, total > 0 ? total : input + output);

        Map<String, Object> details = new LinkedHashMap<>();
        long cacheRead = MapNumberUtils.firstLong(source, CACHE_READ);
        if (cacheRead > 0) {
            // Responses 的明细键是 cached_tokens（不是 Messages 的 cache_read_input_tokens）
            details.put(CACHED_TOKENS, cacheRead);
        }
        long cacheCreation = MapNumberUtils.firstLong(source, CACHE_CREATION);
        if (cacheCreation > 0) {
            details.put(CACHE_CREATION, cacheCreation);
        }
        if (!details.isEmpty()) {
            usage.put(INPUT_TOKENS_DETAILS, details);
        }
        target.put(AiPayloadFields.USAGE, usage);
    }

    /**
     * OpenAI 口径 usage → Messages 口径：{@code prompt→input}、{@code completion→output}，
     * 不带缓存键。
     */
    private static Map<String, Object> anthropicUsage(Object usageNode) {
        Map<String, Object> source = usageOf(usageNode);
        if (source == null) {
            return Map.of();
        }
        Map<String, Object> usage = new LinkedHashMap<>();
        PayloadMapSupport.putLong(usage, AiPayloadFields.INPUT_TOKENS,
                MapNumberUtils.firstLong(source, AiPayloadFields.PROMPT_TOKENS, AiPayloadFields.INPUT_TOKENS));
        PayloadMapSupport.putLong(usage, AiPayloadFields.OUTPUT_TOKENS,
                MapNumberUtils.firstLong(source, AiPayloadFields.COMPLETION_TOKENS, AiPayloadFields.OUTPUT_TOKENS));
        return usage;
    }

    private static Map<String, Object> usageOf(Object usageNode) {
        return usageNode instanceof Map<?, ?> raw && !raw.isEmpty() ? PayloadMapSupport.cast(raw) : null;
    }

    private static void putPositive(Map<String, Object> target, Map<String, Object> source, String key) {
        long value = MapNumberUtils.firstLong(source, key);
        if (value > 0) {
            target.put(key, value);
        }
    }

    /**
     * Anthropic {@code stop_reason} → OpenAI {@code finish_reason}，公开给流式转换
     * （{@code AnthropicStreamSupport}）复用。
     */
    public static String finishReason(Object stopReason) {
        String raw = StringUtils.trimToEmpty(PayloadMapSupport.stringOf(stopReason));
        return FINISH_REASONS.getOrDefault(raw, "stop");
    }

    /** OpenAI {@code finish_reason} → Anthropic {@code stop_reason}（反向；流式同用本表）。 */
    public static String stopReason(Object finishReason) {
        String raw = StringUtils.trimToEmpty(PayloadMapSupport.stringOf(finishReason));
        return STOP_REASONS.getOrDefault(raw, "end_turn");
    }

    private static Object modelOf(UpstreamRequestContext context) {
        return context.model() == null ? null : context.model().upstreamModel();
    }

    /** 序列化失败时回退原串（与 Converse 侧同一约定：宁可透传也不发出半个形状）。 */
    private static String jsonOrOriginal(Map<String, Object> converted, String original) {
        String json = JsonKit.toJson(converted);
        return json == null ? original : json;
    }
}
