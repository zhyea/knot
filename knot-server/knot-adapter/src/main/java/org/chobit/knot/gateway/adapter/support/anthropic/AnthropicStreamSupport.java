package org.chobit.knot.gateway.adapter.support.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.protocol.StreamLineAdapter;
import org.chobit.knot.gateway.adapter.support.PayloadMapSupport;
import org.chobit.knot.gateway.adapter.support.SseLineSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.util.MapNumberUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anthropic Messages SSE ↔ OpenAI 系 SSE 的双向流式转换（协议级，不隶属单一供应商）。
 *
 * <p>与整包侧 {@link AnthropicMessagesResponseSupport} 对称：那边一个方法服务一个适配器的
 * {@code handleResponse}，这边一个工厂方法服务一个适配器的 {@link StreamLineAdapter}。
 * 逐事件语义的知识只住本类，词表（{@code stop_reason} ↔ {@code finish_reason}）仍归
 * {@code AnthropicMessagesResponseSupport}——同一张表不能有两个所有者。</p>
 *
 * <p>为什么工厂方法而不是静态无状态：流式转换有<b>必然的跨事件状态</b>——chunk id 必须全程一致、
 * {@code message_start} 只许合成一次、{@code tool_use} 块序号要连续。适配器是单例 Bean，
 * 状态挂在实例上会被并发请求互相污染，故每次请求新建一个。</p>
 *
 * <p>四条约定：</p>
 * <ul>
 *   <li><b>抑制噪音</b>：{@code event:} 行对 OpenAI 客户端无意义、{@code ping} 事件无对应物，
 *       一律不发；空白行（SSE 事件分隔符）必须保留，否则两个事件会黏成一条；</li>
 *   <li><b>不认识就原样发</b>：非 JSON 载荷、未知事件类型、{@code error} 事件逐字节透传，
 *       绝不为了「形状统一」吞掉上游消息；</li>
 *   <li><b>只转有对应物的事件</b>：{@code thinking_delta} / {@code signature_delta} 在 OpenAI 系
 *       没有承载位，丢弃（这类内容属于扩展推理，OpenAI 客户端本就无法消费）；</li>
 *   <li><b>工具块按需合成</b>：{@code content_block_start(tool_use)} 要翻译成 OpenAI 的
 *       {@code delta.tool_calls} 首片（带 id 与 name），后续 {@code input_json_delta} 是
 *       {@code arguments} 增量；反向则合成 {@code event: content_block_start} 等整套事件行。</li>
 * </ul>
 */
public final class AnthropicStreamSupport {

    private static final String TYPE = AiPayloadFields.TYPE;
    private static final String INDEX = AiPayloadFields.INDEX;
    private static final String DELTA = AiPayloadFields.DELTA;
    private static final String USAGE = AiPayloadFields.USAGE;
    private static final String MESSAGE = "message";
    private static final String CONTENT_BLOCK = "content_block";
    private static final String STOP_REASON = "stop_reason";
    private static final String FINISH_REASON = "finish_reason";
    private static final String CHOICES = "choices";
    private static final String OBJECT = "object";
    private static final String CREATED = "created";
    private static final String CREATED_AT = "created_at";
    private static final String OUTPUT = "output";
    private static final String OUTPUT_INDEX = "output_index";
    private static final String CONTENT_INDEX = "content_index";
    private static final String ITEM_ID = "item_id";
    private static final String ITEM = "item";
    private static final String PART = "part";
    private static final String STATUS = "status";
    private static final String ASSISTANT = "assistant";

    // Anthropic 事件类型
    private static final String EVENT_MESSAGE_START = "message_start";
    private static final String EVENT_MESSAGE_DELTA = "message_delta";
    private static final String EVENT_MESSAGE_STOP = "message_stop";
    private static final String EVENT_CONTENT_BLOCK_START = "content_block_start";
    private static final String EVENT_CONTENT_BLOCK_DELTA = "content_block_delta";
    private static final String EVENT_CONTENT_BLOCK_STOP = "content_block_stop";
    private static final String EVENT_PING = "ping";
    private static final String EVENT_ERROR = "error";
    private static final String DELTA_TEXT = "text_delta";
    private static final String DELTA_INPUT_JSON = "input_json_delta";
    private static final String PARTIAL_JSON = "partial_json";

    // OpenAI 形状
    private static final String CHAT_CHUNK = "chat.completion.chunk";
    private static final String TEXT_COMPLETION = "text_completion";
    private static final String RESPONSE_OBJECT = "response";
    private static final String OUTPUT_TEXT = "output_text";
    private static final String TOOL_CALLS = AiPayloadFields.TOOL_CALLS;
    private static final String TOOL_USE = AiPayloadFields.TOOL_USE;
    private static final String FUNCTION = AiPayloadFields.FUNCTION;
    private static final String ARGUMENTS = AiPayloadFields.ARGUMENTS;
    private static final String ANNOTATIONS = "annotations";
    private static final String EMPTY_ARGUMENTS = "";

    private AnthropicStreamSupport() {
    }

    /** Messages SSE → {@code chat.completion.chunk} SSE（服务 ChatCompletionsToMessages 适配器）。 */
    public static StreamLineAdapter messagesToChatCompletionChunks(UpstreamRequestContext context) {
        return new ChatCompletionChunkAdapter(context);
    }

    /** Messages SSE → 旧版 {@code text_completion} SSE（服务 CompletionsToMessages 适配器）。 */
    public static StreamLineAdapter messagesToTextCompletionChunks(UpstreamRequestContext context) {
        return new TextCompletionChunkAdapter(context);
    }

    /** Messages SSE → Responses 事件流（服务 ResponsesToMessages 适配器）。 */
    public static StreamLineAdapter messagesToResponsesEvents(UpstreamRequestContext context) {
        return new ResponsesEventAdapter(context);
    }

    /** {@code chat.completion.chunk} SSE → Messages SSE（服务 MessagesToChatCompletions 适配器）。 */
    public static StreamLineAdapter chatChunksToMessages(UpstreamRequestContext context) {
        return new MessagesEventAdapter(context);
    }

    // ==================================================================================
    // 方向一：Messages → chat.completion.chunk
    // ==================================================================================

    private static final class ChatCompletionChunkAdapter implements StreamLineAdapter {

        private final UpstreamRequestContext context;
        private final long created = Instant.now().getEpochSecond();
        private final Map<Integer, Integer> toolIndexByBlock = new HashMap<>();
        private String id;
        private Object model;
        private int nextToolIndex;
        private long promptTokens;
        private long completionTokens;

        private ChatCompletionChunkAdapter(UpstreamRequestContext context) {
            this.context = context;
        }

        @Override
        public List<String> adapt(String line) {
            if (StringUtils.isBlank(line)) {
                return List.of(SseLineSupport.BLANK);
            }
            if (SseLineSupport.isEvent(line)) {
                return List.of();
            }
            Map<String, Object> event = eventOf(line);
            if (event == null) {
                return List.of(line);
            }
            return switch (PayloadMapSupport.stringOf(event.get(TYPE))) {
                case EVENT_MESSAGE_START -> messageStart(event);
                case EVENT_CONTENT_BLOCK_START -> contentBlockStart(event);
                case EVENT_CONTENT_BLOCK_DELTA -> contentBlockDelta(event);
                case EVENT_MESSAGE_DELTA -> messageDelta(event);
                case EVENT_MESSAGE_STOP -> List.of(SseLineSupport.data(SseLineSupport.doneMarker()));
                // content_block_stop / ping：OpenAI chunk 无对应物
                case EVENT_CONTENT_BLOCK_STOP, EVENT_PING -> List.of();
                default -> List.of(line);
            };
        }

        private List<String> messageStart(Map<String, Object> event) {
            Map<String, Object> message = asMap(event.get(MESSAGE));
            id = "chatcmpl-" + StringUtils.defaultIfBlank(
                    PayloadMapSupport.text(message, AiPayloadFields.ID), UUID.randomUUID().toString());
            model = message.get(AiPayloadFields.MODEL) == null
                    ? modelOf(context) : message.get(AiPayloadFields.MODEL);
            promptTokens = MapNumberUtils.firstLong(asMap(message.get(USAGE)), AiPayloadFields.INPUT_TOKENS);

            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put(AiPayloadFields.ROLE, ASSISTANT);
            delta.put(AiPayloadFields.CONTENT, "");
            return List.of(SseLineSupport.data(JsonKit.toJson(chunk(delta, null, null))));
        }

        private List<String> contentBlockStart(Map<String, Object> event) {
            Map<String, Object> block = asMap(event.get(CONTENT_BLOCK));
            if (!TOOL_USE.equals(PayloadMapSupport.stringOf(block.get(TYPE)))) {
                // text 空块 / thinking 块：没有可发的增量
                return List.of();
            }
            int blockIndex = (int) MapNumberUtils.firstLong(event, INDEX);
            int toolIndex = nextToolIndex++;
            toolIndexByBlock.put(blockIndex, toolIndex);

            Map<String, Object> function = new LinkedHashMap<>();
            function.put(AiPayloadFields.NAME, PayloadMapSupport.text(block, AiPayloadFields.NAME));
            function.put(ARGUMENTS, EMPTY_ARGUMENTS);

            Map<String, Object> call = new LinkedHashMap<>();
            call.put(INDEX, toolIndex);
            call.put(AiPayloadFields.ID, PayloadMapSupport.text(block, AiPayloadFields.ID));
            call.put(TYPE, FUNCTION);
            call.put(FUNCTION, function);

            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put(TOOL_CALLS, List.of(call));
            return List.of(SseLineSupport.data(JsonKit.toJson(chunk(delta, null, null))));
        }

        private List<String> contentBlockDelta(Map<String, Object> event) {
            Map<String, Object> delta = asMap(event.get(DELTA));
            String deltaType = PayloadMapSupport.stringOf(delta.get(TYPE));
            if (DELTA_TEXT.equals(deltaType)) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put(AiPayloadFields.CONTENT, PayloadMapSupport.stringOf(delta.get(AiPayloadFields.TEXT)));
                return List.of(SseLineSupport.data(JsonKit.toJson(chunk(out, null, null))));
            }
            if (!DELTA_INPUT_JSON.equals(deltaType)) {
                // thinking_delta / signature_delta：OpenAI 系无承载位
                return List.of();
            }
            Integer toolIndex = toolIndexByBlock.get((int) MapNumberUtils.firstLong(event, INDEX));
            if (toolIndex == null) {
                // 没见过对应的 content_block_start(tool_use)，无法归属到某个 tool_call
                return List.of();
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put(ARGUMENTS, PayloadMapSupport.stringOf(delta.get(PARTIAL_JSON)));

            Map<String, Object> call = new LinkedHashMap<>();
            call.put(INDEX, toolIndex);
            call.put(FUNCTION, function);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put(TOOL_CALLS, List.of(call));
            return List.of(SseLineSupport.data(JsonKit.toJson(chunk(out, null, null))));
        }

        private List<String> messageDelta(Map<String, Object> event) {
            Map<String, Object> delta = asMap(event.get(DELTA));
            completionTokens = MapNumberUtils.firstLong(asMap(event.get(USAGE)), AiPayloadFields.OUTPUT_TOKENS);
            String finish = AnthropicMessagesResponseSupport.finishReason(delta.get(STOP_REASON));
            return List.of(SseLineSupport.data(JsonKit.toJson(
                    chunk(new LinkedHashMap<>(), finish, openAiUsage()))));
        }

        private Map<String, Object> openAiUsage() {
            if (promptTokens <= 0 && completionTokens <= 0) {
                return null;
            }
            Map<String, Object> usage = new LinkedHashMap<>();
            PayloadMapSupport.putLong(usage, AiPayloadFields.PROMPT_TOKENS, promptTokens);
            PayloadMapSupport.putLong(usage, AiPayloadFields.COMPLETION_TOKENS, completionTokens);
            PayloadMapSupport.putLong(usage, AiPayloadFields.TOTAL_TOKENS, promptTokens + completionTokens);
            return usage;
        }

        private Map<String, Object> chunk(Map<String, Object> delta, String finishReason, Map<String, Object> usage) {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put(INDEX, 0);
            choice.put(DELTA, delta);
            choice.put(FINISH_REASON, finishReason);

            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put(AiPayloadFields.ID, id);
            chunk.put(OBJECT, CHAT_CHUNK);
            chunk.put(CREATED, created);
            chunk.put(AiPayloadFields.MODEL, model);
            chunk.put(CHOICES, List.of(choice));
            if (usage != null) {
                chunk.put(USAGE, usage);
            }
            return chunk;
        }
    }

    // ==================================================================================
    // 方向二：Messages → text_completion chunk
    // ==================================================================================

    private static final class TextCompletionChunkAdapter implements StreamLineAdapter {

        private final UpstreamRequestContext context;
        private final long created = Instant.now().getEpochSecond();
        private String id;
        private Object model;
        private long promptTokens;
        private long completionTokens;

        private TextCompletionChunkAdapter(UpstreamRequestContext context) {
            this.context = context;
        }

        @Override
        public List<String> adapt(String line) {
            if (StringUtils.isBlank(line)) {
                return List.of(SseLineSupport.BLANK);
            }
            if (SseLineSupport.isEvent(line)) {
                return List.of();
            }
            Map<String, Object> event = eventOf(line);
            if (event == null) {
                return List.of(line);
            }
            return switch (PayloadMapSupport.stringOf(event.get(TYPE))) {
                case EVENT_MESSAGE_START -> messageStart(event);
                case EVENT_CONTENT_BLOCK_DELTA -> contentBlockDelta(event);
                case EVENT_MESSAGE_DELTA -> messageDelta(event);
                case EVENT_MESSAGE_STOP -> List.of(SseLineSupport.data(SseLineSupport.doneMarker()));
                // 旧版 Completions 协议没有 role / 工具概念：块起始与 thinking 一律丢弃
                case EVENT_CONTENT_BLOCK_START, EVENT_CONTENT_BLOCK_STOP, EVENT_PING -> List.of();
                default -> List.of(line);
            };
        }

        private List<String> messageStart(Map<String, Object> event) {
            Map<String, Object> message = asMap(event.get(MESSAGE));
            id = "cmpl-" + StringUtils.defaultIfBlank(
                    PayloadMapSupport.text(message, AiPayloadFields.ID), UUID.randomUUID().toString());
            model = message.get(AiPayloadFields.MODEL) == null
                    ? modelOf(context) : message.get(AiPayloadFields.MODEL);
            promptTokens = MapNumberUtils.firstLong(asMap(message.get(USAGE)), AiPayloadFields.INPUT_TOKENS);
            // text_completion 首个有效载荷就是文本本身，此处不发空 chunk
            return List.of();
        }

        private List<String> contentBlockDelta(Map<String, Object> event) {
            Map<String, Object> delta = asMap(event.get(DELTA));
            if (!DELTA_TEXT.equals(PayloadMapSupport.stringOf(delta.get(TYPE)))) {
                return List.of();
            }
            return List.of(SseLineSupport.data(JsonKit.toJson(
                    chunk(PayloadMapSupport.stringOf(delta.get(AiPayloadFields.TEXT)), null, null))));
        }

        private List<String> messageDelta(Map<String, Object> event) {
            Map<String, Object> delta = asMap(event.get(DELTA));
            completionTokens = MapNumberUtils.firstLong(asMap(event.get(USAGE)), AiPayloadFields.OUTPUT_TOKENS);
            String finish = AnthropicMessagesResponseSupport.finishReason(delta.get(STOP_REASON));
            return List.of(SseLineSupport.data(JsonKit.toJson(chunk("", finish, openAiUsage()))));
        }

        private Map<String, Object> openAiUsage() {
            if (promptTokens <= 0 && completionTokens <= 0) {
                return null;
            }
            Map<String, Object> usage = new LinkedHashMap<>();
            PayloadMapSupport.putLong(usage, AiPayloadFields.PROMPT_TOKENS, promptTokens);
            PayloadMapSupport.putLong(usage, AiPayloadFields.COMPLETION_TOKENS, completionTokens);
            PayloadMapSupport.putLong(usage, AiPayloadFields.TOTAL_TOKENS, promptTokens + completionTokens);
            return usage;
        }

        private Map<String, Object> chunk(String text, String finishReason, Map<String, Object> usage) {
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put(AiPayloadFields.TEXT, text);
            choice.put(INDEX, 0);
            choice.put(FINISH_REASON, finishReason);

            Map<String, Object> chunk = new LinkedHashMap<>();
            chunk.put(AiPayloadFields.ID, id);
            chunk.put(OBJECT, TEXT_COMPLETION);
            chunk.put(CREATED, created);
            chunk.put(AiPayloadFields.MODEL, model);
            chunk.put(CHOICES, List.of(choice));
            if (usage != null) {
                chunk.put(USAGE, usage);
            }
            return chunk;
        }
    }

    // ==================================================================================
    // 方向三：Messages → Responses 事件流
    // ==================================================================================

    private static final class ResponsesEventAdapter implements StreamLineAdapter {

        private final UpstreamRequestContext context;
        private final long createdAt = Instant.now().getEpochSecond();
        private final StringBuilder text = new StringBuilder();
        private String responseId;
        private String itemId;
        private Object model;
        private long inputTokens;
        private long outputTokens;
        private int outputIndex;
        private boolean textPartStarted;

        private ResponsesEventAdapter(UpstreamRequestContext context) {
            this.context = context;
        }

        @Override
        public List<String> adapt(String line) {
            if (StringUtils.isBlank(line)) {
                return List.of(SseLineSupport.BLANK);
            }
            if (SseLineSupport.isEvent(line)) {
                return List.of();
            }
            Map<String, Object> event = eventOf(line);
            if (event == null) {
                return List.of(line);
            }
            return switch (PayloadMapSupport.stringOf(event.get(TYPE))) {
                case EVENT_MESSAGE_START -> messageStart(event);
                case EVENT_CONTENT_BLOCK_START -> contentBlockStart(event);
                case EVENT_CONTENT_BLOCK_DELTA -> contentBlockDelta(event);
                case EVENT_MESSAGE_DELTA -> messageDelta(event);
                case EVENT_MESSAGE_STOP -> messageStop();
                case EVENT_CONTENT_BLOCK_STOP, EVENT_PING -> List.of();
                default -> List.of(line);
            };
        }

        private List<String> messageStart(Map<String, Object> event) {
            Map<String, Object> message = asMap(event.get(MESSAGE));
            responseId = "resp_" + StringUtils.defaultIfBlank(
                    PayloadMapSupport.text(message, AiPayloadFields.ID), UUID.randomUUID().toString());
            itemId = "msg_" + responseId;
            model = message.get(AiPayloadFields.MODEL) == null
                    ? modelOf(context) : message.get(AiPayloadFields.MODEL);
            inputTokens = MapNumberUtils.firstLong(asMap(message.get(USAGE)), AiPayloadFields.INPUT_TOKENS);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put(AiPayloadFields.ID, responseId);
            response.put(OBJECT, RESPONSE_OBJECT);
            response.put(CREATED_AT, createdAt);
            response.put(AiPayloadFields.MODEL, model);
            response.put(STATUS, "in_progress");
            response.put(OUTPUT, List.of());

            List<String> lines = new ArrayList<>(eventLines("response.created",
                    named("response.created", "response", response)));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put(TYPE, MESSAGE);
            item.put(AiPayloadFields.ID, itemId);
            item.put(AiPayloadFields.ROLE, ASSISTANT);
            item.put(STATUS, "in_progress");
            item.put(AiPayloadFields.CONTENT, List.of());
            lines.addAll(outputItemAdded(item));

            Map<String, Object> part = new LinkedHashMap<>();
            part.put(TYPE, OUTPUT_TEXT);
            part.put(AiPayloadFields.TEXT, "");
            part.put(ANNOTATIONS, List.of());
            Map<String, Object> partEvent = named("response.content_part.added", "part", part);
            partEvent.put(ITEM_ID, itemId);
            partEvent.put(OUTPUT_INDEX, 0);
            partEvent.put(CONTENT_INDEX, 0);
            lines.addAll(eventLines("response.content_part.added", partEvent));
            textPartStarted = true;
            return lines;
        }

        private List<String> contentBlockStart(Map<String, Object> event) {
            Map<String, Object> block = asMap(event.get(CONTENT_BLOCK));
            if (!TOOL_USE.equals(PayloadMapSupport.stringOf(block.get(TYPE)))) {
                return List.of();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(TYPE, AiPayloadFields.FUNCTION_CALL);
            item.put(AiPayloadFields.ID, PayloadMapSupport.text(block, AiPayloadFields.ID));
            item.put(AiPayloadFields.NAME, PayloadMapSupport.text(block, AiPayloadFields.NAME));
            item.put(ARGUMENTS, EMPTY_ARGUMENTS);
            item.put(STATUS, "in_progress");
            return outputItemAdded(item);
        }

        private List<String> contentBlockDelta(Map<String, Object> event) {
            Map<String, Object> delta = asMap(event.get(DELTA));
            String deltaType = PayloadMapSupport.stringOf(delta.get(TYPE));
            if (DELTA_TEXT.equals(deltaType)) {
                String piece = PayloadMapSupport.stringOf(delta.get(AiPayloadFields.TEXT));
                text.append(piece);
                Map<String, Object> payload = named("response.output_text.delta", DELTA, piece);
                payload.put(ITEM_ID, itemId);
                payload.put(OUTPUT_INDEX, 0);
                payload.put(CONTENT_INDEX, 0);
                return eventLines("response.output_text.delta", payload);
            }
            if (!DELTA_INPUT_JSON.equals(deltaType)) {
                return List.of();
            }
            Map<String, Object> payload = named("response.function_call_arguments.delta",
                    DELTA, PayloadMapSupport.stringOf(delta.get(PARTIAL_JSON)));
            payload.put(ITEM_ID, itemId);
            payload.put(OUTPUT_INDEX, Math.max(0, outputIndex - 1));
            return eventLines("response.function_call_arguments.delta", payload);
        }

        private List<String> messageDelta(Map<String, Object> event) {
            outputTokens = MapNumberUtils.firstLong(asMap(event.get(USAGE)), AiPayloadFields.OUTPUT_TOKENS);
            // Responses 用 response.completed 统一收尾，中间不逐句发状态
            return List.of();
        }

        private List<String> messageStop() {
            List<String> lines = new ArrayList<>();
            if (textPartStarted) {
                Map<String, Object> done = named("response.output_text.done", AiPayloadFields.TEXT, text.toString());
                done.put(ITEM_ID, itemId);
                done.put(OUTPUT_INDEX, 0);
                done.put(CONTENT_INDEX, 0);
                lines.addAll(eventLines("response.output_text.done", done));

                Map<String, Object> item = new LinkedHashMap<>();
                item.put(TYPE, MESSAGE);
                item.put(AiPayloadFields.ID, itemId);
                item.put(AiPayloadFields.ROLE, ASSISTANT);
                item.put(STATUS, "completed");
                item.put(AiPayloadFields.CONTENT, List.of(outputTextPart(text.toString())));
                Map<String, Object> itemEvent = named("response.output_item.done", ITEM, item);
                itemEvent.put(OUTPUT_INDEX, 0);
                lines.addAll(eventLines("response.output_item.done", itemEvent));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put(AiPayloadFields.ID, responseId);
            response.put(OBJECT, RESPONSE_OBJECT);
            response.put(CREATED_AT, createdAt);
            response.put(AiPayloadFields.MODEL, model);
            response.put(STATUS, "completed");
            response.put(OUTPUT, List.of(outputTextMessage()));
            if (inputTokens > 0 || outputTokens > 0) {
                Map<String, Object> usage = new LinkedHashMap<>();
                PayloadMapSupport.putLong(usage, AiPayloadFields.INPUT_TOKENS, inputTokens);
                PayloadMapSupport.putLong(usage, AiPayloadFields.OUTPUT_TOKENS, outputTokens);
                PayloadMapSupport.putLong(usage, AiPayloadFields.TOTAL_TOKENS, inputTokens + outputTokens);
                response.put(USAGE, usage);
            }
            lines.addAll(eventLines("response.completed", named("response.completed", "response", response)));
            return lines;
        }

        private Map<String, Object> outputTextMessage() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(TYPE, MESSAGE);
            item.put(AiPayloadFields.ID, itemId);
            item.put(AiPayloadFields.ROLE, ASSISTANT);
            item.put(STATUS, "completed");
            item.put(AiPayloadFields.CONTENT, List.of(outputTextPart(text.toString())));
            return item;
        }

        private Map<String, Object> outputTextPart(String value) {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put(TYPE, OUTPUT_TEXT);
            part.put(AiPayloadFields.TEXT, value);
            part.put(ANNOTATIONS, List.of());
            return part;
        }

        private List<String> outputItemAdded(Map<String, Object> item) {
            Map<String, Object> payload = named("response.output_item.added", ITEM, item);
            payload.put(OUTPUT_INDEX, outputIndex);
            outputIndex++;
            return eventLines("response.output_item.added", payload);
        }
    }

    // ==================================================================================
    // 方向四：chat.completion.chunk → Messages
    // ==================================================================================

    private static final class MessagesEventAdapter implements StreamLineAdapter {

        private final UpstreamRequestContext context;
        private final Map<Integer, Integer> blockByToolIndex = new HashMap<>();
        private boolean started;
        private boolean textBlockStarted;
        private int textBlockIndex = -1;
        private int nextBlockIndex;
        private long outputTokens;
        private String stopReason;

        private MessagesEventAdapter(UpstreamRequestContext context) {
            this.context = context;
        }

        @Override
        public List<String> adapt(String line) {
            if (StringUtils.isBlank(line)) {
                return List.of(SseLineSupport.BLANK);
            }
            if (SseLineSupport.isEvent(line)) {
                return List.of();
            }
            if (SseLineSupport.isDone(line)) {
                return finish();
            }
            Map<String, Object> chunk = eventOf(line);
            if (chunk == null) {
                return List.of(line);
            }
            List<String> lines = new ArrayList<>(ensureStarted(chunk));

            Map<String, Object> choice = firstChoice(chunk);
            if (choice == null) {
                return lines;
            }
            Map<String, Object> usage = asMap(chunk.get(USAGE));
            outputTokens = Math.max(outputTokens,
                    MapNumberUtils.firstLong(usage, AiPayloadFields.COMPLETION_TOKENS));

            Map<String, Object> delta = asMap(choice.get(DELTA));
            lines.addAll(textDelta(PayloadMapSupport.stringOf(delta.get(AiPayloadFields.CONTENT))));
            lines.addAll(toolCallDeltas(delta.get(TOOL_CALLS)));

            String finishReason = PayloadMapSupport.text(choice, FINISH_REASON);
            if (finishReason != null) {
                stopReason = AnthropicMessagesResponseSupport.stopReason(finishReason);
            }
            return lines;
        }

        /** 首个 chunk 之前补齐 {@code message_start}（content 块按需延迟到真有内容时再开）。 */
        private List<String> ensureStarted(Map<String, Object> chunk) {
            if (started) {
                return List.of();
            }
            started = true;
            String messageId = "msg_" + StringUtils.defaultIfBlank(
                    PayloadMapSupport.text(chunk, AiPayloadFields.ID), UUID.randomUUID().toString());
            Object model = chunk.get(AiPayloadFields.MODEL) == null
                    ? modelOf(context) : chunk.get(AiPayloadFields.MODEL);
            long inputTokens = MapNumberUtils.firstLong(asMap(chunk.get(USAGE)), AiPayloadFields.PROMPT_TOKENS);

            Map<String, Object> message = new LinkedHashMap<>();
            message.put(AiPayloadFields.ID, messageId);
            message.put(TYPE, MESSAGE);
            message.put(AiPayloadFields.ROLE, ASSISTANT);
            message.put(AiPayloadFields.MODEL, model);
            message.put(AiPayloadFields.CONTENT, List.of());
            message.put(STOP_REASON, null);
            message.put(USAGE, Map.of(AiPayloadFields.INPUT_TOKENS, inputTokens, AiPayloadFields.OUTPUT_TOKENS, 0));

            return eventLines(EVENT_MESSAGE_START, named(EVENT_MESSAGE_START, MESSAGE, message));
        }

        /**
         * text 块延迟开：纯工具调用的轮次没有文本，提前开块会留下一个空 text 块。
         * Anthropic 的块起始必须早于该块的任何 delta，故这里在首个非空增量前补齐。
         */
        private List<String> textBlockStart() {
            if (textBlockStarted) {
                return List.of();
            }
            textBlockStarted = true;
            textBlockIndex = nextBlockIndex++;

            Map<String, Object> block = new LinkedHashMap<>();
            block.put(TYPE, AiPayloadFields.TEXT);
            block.put(AiPayloadFields.TEXT, "");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(TYPE, EVENT_CONTENT_BLOCK_START);
            payload.put(INDEX, textBlockIndex);
            payload.put(CONTENT_BLOCK, block);
            return eventLines(EVENT_CONTENT_BLOCK_START, payload);
        }

        private List<String> textDelta(String piece) {
            if (StringUtils.isEmpty(piece)) {
                return List.of();
            }
            List<String> lines = new ArrayList<>(textBlockStart());

            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put(TYPE, DELTA_TEXT);
            delta.put(AiPayloadFields.TEXT, piece);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(TYPE, EVENT_CONTENT_BLOCK_DELTA);
            payload.put(INDEX, textBlockIndex);
            payload.put(DELTA, delta);
            lines.addAll(eventLines(EVENT_CONTENT_BLOCK_DELTA, payload));
            return lines;
        }

        private List<String> toolCallDeltas(Object toolCalls) {
            if (!(toolCalls instanceof List<?> calls) || calls.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            for (Object item : calls) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> call = PayloadMapSupport.cast(raw);
                int toolIndex = (int) MapNumberUtils.firstLong(call, INDEX);
                Map<String, Object> function = asMap(call.get(FUNCTION));
                String name = PayloadMapSupport.text(function, AiPayloadFields.NAME);
                if (name != null) {
                    int blockIndex = nextBlockIndex++;
                    blockByToolIndex.put(toolIndex, blockIndex);
                    lines.addAll(toolBlockStart(call, name, blockIndex));
                }
                String arguments = PayloadMapSupport.stringOf(function.get(ARGUMENTS));
                Integer blockIndex = blockByToolIndex.get(toolIndex);
                if (!arguments.isEmpty() && blockIndex != null) {
                    lines.addAll(toolArgumentDelta(arguments, blockIndex));
                }
            }
            return lines;
        }

        private List<String> toolBlockStart(Map<String, Object> call, String name, int blockIndex) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put(TYPE, TOOL_USE);
            block.put(AiPayloadFields.ID, PayloadMapSupport.text(call, AiPayloadFields.ID));
            block.put(AiPayloadFields.NAME, name);
            block.put(AiPayloadFields.INPUT, Map.of());

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(TYPE, EVENT_CONTENT_BLOCK_START);
            payload.put(INDEX, blockIndex);
            payload.put(CONTENT_BLOCK, block);
            return eventLines(EVENT_CONTENT_BLOCK_START, payload);
        }

        private List<String> toolArgumentDelta(String arguments, int blockIndex) {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put(TYPE, DELTA_INPUT_JSON);
            delta.put(PARTIAL_JSON, arguments);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(TYPE, EVENT_CONTENT_BLOCK_DELTA);
            payload.put(INDEX, blockIndex);
            payload.put(DELTA, delta);
            return eventLines(EVENT_CONTENT_BLOCK_DELTA, payload);
        }

        /** {@code [DONE]}：补齐 content_block_stop / message_delta / message_stop 三件事。 */
        private List<String> finish() {
            List<String> lines = new ArrayList<>();
            if (!started) {
                // 上游一个 chunk 都没发（空流）：不合成假消息，只给收尾
                return eventLines(EVENT_MESSAGE_STOP, named(EVENT_MESSAGE_STOP, null, null));
            }
            lines.addAll(contentBlockStop());
            lines.addAll(messageDelta());
            lines.addAll(eventLines(EVENT_MESSAGE_STOP, named(EVENT_MESSAGE_STOP, null, null)));
            return lines;
        }

        /** 每个开过的 content 块都要收口，且必须逐个发（Anthropic 客户端按 index 配对）。 */
        private List<String> contentBlockStop() {
            List<String> lines = new ArrayList<>();
            for (int index = nextBlockIndex - 1; index >= 0; index--) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put(TYPE, EVENT_CONTENT_BLOCK_STOP);
                payload.put(INDEX, index);
                lines.addAll(eventLines(EVENT_CONTENT_BLOCK_STOP, payload));
            }
            return lines;
        }

        private List<String> messageDelta() {
            Map<String, Object> delta = new LinkedHashMap<>();
            delta.put(STOP_REASON, stopReason == null ? "end_turn" : stopReason);
            delta.put("stop_sequence", null);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(TYPE, EVENT_MESSAGE_DELTA);
            payload.put(DELTA, delta);
            if (outputTokens > 0) {
                payload.put(USAGE, Map.of(AiPayloadFields.OUTPUT_TOKENS, outputTokens));
            }
            return eventLines(EVENT_MESSAGE_DELTA, payload);
        }
    }

    // ==================================================================================
    // 公共辅助
    // ==================================================================================

    /** 一行 → 事件对象；非 data 行、非 JSON、{@code [DONE]} 一律返回 {@code null} 交给调用方原样处理。 */
    private static Map<String, Object> eventOf(String line) {
        String payload = SseLineSupport.dataOf(line);
        if (StringUtils.isBlank(payload) || SseLineSupport.doneMarker().equals(payload)) {
            return null;
        }
        return JsonKit.fromJson(payload, new TypeReference<>() {
        });
    }

    /** 合成一个 SSE 事件：{@code event:} 行 + {@code data:} 行 + 分隔空行。 */
    private static List<String> eventLines(String eventName, Map<String, Object> payload) {
        String json = JsonKit.toJson(payload);
        if (json == null) {
            return List.of();
        }
        return List.of(SseLineSupport.event(eventName), SseLineSupport.data(json), SseLineSupport.BLANK);
    }

    /** 构造只含 {@code type} 与附加单字段的事件对象（{@code value} 为 {@code null} 时省略该字段）。 */
    private static Map<String, Object> named(String type, String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TYPE, type);
        if (key != null && value != null) {
            payload.put(key, value);
        }
        return payload;
    }

    private static Map<String, Object> firstChoice(Map<String, Object> chunk) {
        if (!(chunk.get(CHOICES) instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        return first instanceof Map<?, ?> raw ? PayloadMapSupport.cast(raw) : null;
    }

    private static Map<String, Object> asMap(Object node) {
        return node instanceof Map<?, ?> raw ? PayloadMapSupport.cast(raw) : Map.of();
    }

    private static Object modelOf(UpstreamRequestContext context) {
        return context == null || context.model() == null ? null : context.model().upstreamModel();
    }
}
