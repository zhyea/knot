package org.chobit.knot.gateway.adapter.support.anthropic;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.gateway.adapter.support.PayloadMapSupport;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.util.JsonKit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具语义的 Anthropic Messages ↔ OpenAI 系双向转换（协议级，不隶属单一供应商）。
 *
 * <p>工具调用是<b>跨三类载体的同一份语义</b>，本类把三处一次性收齐，避免各适配器各写一份：
 * <ol>
 *   <li><b>请求侧声明</b>：{@code tools} / {@code tool_choice} 的形状；
 *   <li><b>消息载体</b>：OpenAI 用 {@code assistant.tool_calls} + {@code role:tool} 消息，
 *       Anthropic 用 {@code assistant.content[].tool_use} + {@code user.content[].tool_result}；
 *   <li><b>响应侧产物</b>：整包 {@code tool_calls} / {@code tool_use} 块、流式 {@code tool_calls} 增量。
 * </ol>
 *
 * <p>三条约定（与 {@code AnthropicMessagesResponseSupport} 同风格）：</p>
 * <ul>
 *   <li><b>形状不认识就不动</b>：入参不是预期的 OpenAI/Anthropic 形状时返回 {@code null}
 *       （调用方据此保留原值），绝不把第三方自定义形状猜着改；</li>
 *   <li><b>不丢内容</b>：消息里出现未知 content 块类型（{@code image} 等）时整条消息原样保留，
 *       不做块级重写——半懂比不懂更危险；</li>
 *   <li><b>参数两侧编码不同</b>：Anthropic 的 {@code input} 是对象，OpenAI 的 {@code arguments}
 *       是 JSON 文本。对象→文本用 {@link JsonKit#toJson}，文本→对象解析失败退化为空对象
 *       （Anthropic 要求 {@code input} 必须是对象，退化成空对象好过整个请求 400）。</li>
 * </ul>
 */
public final class AnthropicToolSupport {

    private static final String AUTO = "auto";
    private static final String NONE = "none";
    private static final String ANY = "any";
    private static final String REQUIRED = "required";
    private static final String TEXT = AiPayloadFields.TEXT;
    private static final String EMPTY_ARGUMENTS = "{}";

    /** {@code tool_choice} 的 Anthropic 档位集合（含 {@code tool} 具名档）。 */
    private static final String CHOICE_TOOL = "tool";

    private AnthropicToolSupport() {
    }

    // ============================ 请求侧：OpenAI → Anthropic ============================

    /**
     * OpenAI 系 {@code tools} → Anthropic {@code tools}：{@code parameters} 改名为 {@code input_schema}，
     * 剥掉 chat 形状的 {@code function} 外层包装。
     *
     * <p>同时吃 OpenAI 的两种规范形状——chat.completions 的
     * {@code [{type:function, function:{name,description,parameters}}]} 与 Responses 的
     * 扁平 {@code [{type:function, name, description, parameters}]}。识别判据是「读得到
     * {@code name}」：有 {@code function} 对象就下钻一层，否则视自身为函数体。
     * 已经是 Anthropic 形状时本方法幂等（{@code input_schema} 原样保留）。</p>
     *
     * @return 转换结果；一个 {@code name} 都读不到时返回 {@code null}（形状不认识，调用方保留原值）
     */
    public static Object toolsToAnthropic(Object tools) {
        if (!(tools instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> converted = new ArrayList<>(list.size());
        boolean recognized = false;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> tool = PayloadMapSupport.cast(raw);
            Map<String, Object> function = functionBody(tool);
            if (PayloadMapSupport.text(function, AiPayloadFields.NAME) == null) {
                continue;
            }
            recognized = true;
            converted.add(anthropicTool(function));
        }
        return recognized ? converted : null;
    }

    /**
     * OpenAI 系 {@code tool_choice} → Anthropic {@code tool_choice}：
     * {@code auto→{type:auto}}、{@code none→{type:none}}、{@code required→{type:any}}、
     * 具名档 {@code {type:function,...}}→{@code {type:tool,name}}（chat 的 {@code function}
     * 包装与 Responses 的扁平形状都认）。
     *
     * @return 转换结果；已是 Anthropic 形状（幂等）或无法识别时返回 {@code null} 表示「调用方别动」
     */
    public static Object toolChoiceToAnthropic(Object choice) {
        if (choice instanceof String value) {
            return switch (value) {
                case AUTO -> Map.of(AiPayloadFields.TYPE, AUTO);
                case NONE -> Map.of(AiPayloadFields.TYPE, NONE);
                case REQUIRED -> Map.of(AiPayloadFields.TYPE, ANY);
                default -> null;
            };
        }
        if (!(choice instanceof Map<?, ?> raw)) {
            return null;
        }
        String name = PayloadMapSupport.text(functionBody(PayloadMapSupport.cast(raw)), AiPayloadFields.NAME);
        if (name == null) {
            // 已是 Anthropic 形状（type 为 auto/any/none/tool 且无 name 可读）或未知形状：不动
            return null;
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        converted.put(AiPayloadFields.TYPE, CHOICE_TOOL);
        converted.put(AiPayloadFields.NAME, name);
        return converted;
    }

    /**
     * OpenAI 消息数组 → Anthropic 消息数组，只重写<b>带工具语义</b>的两类消息：
     * {@code assistant.tool_calls} 折成 {@code tool_use} 块；{@code role:tool} 折成
     * {@code user} 消息里的 {@code tool_result} 块（连续多条 tool 结果合并进同一条 user 消息，
     * 对齐 Anthropic「tool_use 之后紧跟 tool_result」的配对要求）。
     *
     * <p>其余消息与无法识别的形状原样保留——本方法只回答工具载体怎么换，不重排普通会话。</p>
     */
    public static List<Map<String, Object>> messagesToAnthropic(List<?> messages) {
        List<Map<String, Object>> converted = new ArrayList<>(messages.size());
        List<Map<String, Object>> pendingResults = new ArrayList<>();
        for (Object item : messages) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> message = PayloadMapSupport.cast(raw);
            if (isToolResultMessage(message)) {
                pendingResults.add(toolResultBlock(message));
                continue;
            }
            flushToolResults(pendingResults, converted);
            converted.add(rewriteAssistant(message));
        }
        flushToolResults(pendingResults, converted);
        return converted;
    }

    // ============================ 请求侧：Anthropic → OpenAI ============================

    /**
     * Anthropic {@code tools} → OpenAI {@code tools}：{@code name/description/input_schema}
     * 套上 {@code {type:function, function:{...}}} 两层壳，{@code input_schema} 改名 {@code parameters}。
     *
     * @return 转换结果；入参不是「含 {@code name} 的对象数组」时返回 {@code null}
     */
    public static Object toolsToOpenAi(Object tools) {
        if (!(tools instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> converted = new ArrayList<>(list.size());
        boolean recognized = false;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> tool = PayloadMapSupport.cast(raw);
            String name = PayloadMapSupport.text(tool, AiPayloadFields.NAME);
            if (name == null) {
                continue;
            }
            recognized = true;
            Map<String, Object> function = new LinkedHashMap<>();
            function.put(AiPayloadFields.NAME, name);
            copyIfPresent(tool, function, AiPayloadFields.DESCRIPTION);
            if (tool.get(AiPayloadFields.INPUT_SCHEMA) != null) {
                function.put(AiPayloadFields.PARAMETERS, tool.get(AiPayloadFields.INPUT_SCHEMA));
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put(AiPayloadFields.TYPE, AiPayloadFields.FUNCTION);
            wrapper.put(AiPayloadFields.FUNCTION, function);
            converted.add(wrapper);
        }
        return recognized ? converted : null;
    }

    /**
     * Anthropic {@code tool_choice} → OpenAI {@code tool_choice}：{@code {type:any}}→{@code required}、
     * {@code {type:tool,name}}→{@code {type:function,function:{name}}}，{@code auto/none} 直接给字符串档。
     *
     * @return 转换结果；无法识别时返回 {@code null}
     */
    public static Object toolChoiceToOpenAi(Object choice) {
        if (!(choice instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> map = PayloadMapSupport.cast(raw);
        String type = PayloadMapSupport.text(map, AiPayloadFields.TYPE);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case AUTO -> AUTO;
            case ANY -> REQUIRED;
            case NONE -> NONE;
            case CHOICE_TOOL -> namedToolChoice(PayloadMapSupport.text(map, AiPayloadFields.NAME));
            default -> null;
        };
    }

    /**
     * Anthropic 消息数组 → OpenAI 消息数组。{@code tool_use} 块折成
     * {@code assistant.tool_calls}；{@code tool_result} 块拆成独立的 {@code role:tool} 消息
     * （一条 Anthropic user 消息可能因此产出多条 OpenAI 消息，故返回列表长度不固定）。
     *
     * <p>content 里出现未知块类型时整条消息原样保留（不丢内容），见类注释第二条约定。</p>
     */
    public static List<Map<String, Object>> messagesToOpenAi(List<?> messages) {
        List<Map<String, Object>> converted = new ArrayList<>(messages.size());
        for (Object item : messages) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> message = PayloadMapSupport.cast(raw);
            if (!(message.get(AiPayloadFields.CONTENT) instanceof List<?> blocks) || !recognizable(blocks)) {
                converted.add(message);
                continue;
            }
            converted.addAll(splitBlocks(message, blocks));
        }
        return converted;
    }

    // ============================ 响应侧：块级转换 ============================

    /**
     * Messages {@code content} 中的 {@code tool_use} 块 → OpenAI {@code tool_calls}。
     *
     * @return 无 {@code tool_use} 块时返回空列表（调用方据此决定是否写 {@code tool_calls} 字段）
     */
    public static List<Map<String, Object>> toolCallsOf(Object content) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (Map<String, Object> block : blocksOf(content)) {
            if (!AiPayloadFields.TOOL_USE.equals(block.get(AiPayloadFields.TYPE))) {
                continue;
            }
            Map<String, Object> function = new LinkedHashMap<>();
            function.put(AiPayloadFields.NAME, PayloadMapSupport.text(block, AiPayloadFields.NAME));

            Map<String, Object> call = new LinkedHashMap<>();
            call.put(AiPayloadFields.ID, PayloadMapSupport.text(block, AiPayloadFields.ID));
            call.put(AiPayloadFields.TYPE, AiPayloadFields.FUNCTION);
            call.put(AiPayloadFields.FUNCTION, function);
            function.put(AiPayloadFields.ARGUMENTS, argumentsOf(block.get(AiPayloadFields.INPUT)));
            calls.add(call);
        }
        return calls;
    }

    /** OpenAI {@code tool_calls} → Anthropic {@code tool_use} 块（{@code arguments} 反解析成 {@code input} 对象）。 */
    public static List<Map<String, Object>> toolUseBlocksOf(Object toolCalls) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (!(toolCalls instanceof List<?> calls)) {
            return blocks;
        }
        for (Object item : calls) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> call = PayloadMapSupport.cast(raw);
            Map<String, Object> function = call.get(AiPayloadFields.FUNCTION) instanceof Map<?, ?> fnRaw
                    ? PayloadMapSupport.cast(fnRaw) : Map.of();

            Map<String, Object> block = new LinkedHashMap<>();
            block.put(AiPayloadFields.TYPE, AiPayloadFields.TOOL_USE);
            block.put(AiPayloadFields.ID, PayloadMapSupport.text(call, AiPayloadFields.ID));
            block.put(AiPayloadFields.NAME, PayloadMapSupport.text(function, AiPayloadFields.NAME));
            block.put(AiPayloadFields.INPUT, inputOf(function.get(AiPayloadFields.ARGUMENTS)));
            blocks.add(block);
        }
        return blocks;
    }

    /**
     * Messages {@code content} 中的 {@code tool_use} 块 → Responses 的 {@code function_call} output 项。
     *
     * @return 无 {@code tool_use} 块时返回空列表
     */
    public static List<Map<String, Object>> functionCallsOf(Object content) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> block : blocksOf(content)) {
            if (!AiPayloadFields.TOOL_USE.equals(block.get(AiPayloadFields.TYPE))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put(AiPayloadFields.TYPE, AiPayloadFields.FUNCTION_CALL);
            item.put(AiPayloadFields.ID, PayloadMapSupport.text(block, AiPayloadFields.ID));
            item.put(AiPayloadFields.NAME, PayloadMapSupport.text(block, AiPayloadFields.NAME));
            item.put(AiPayloadFields.ARGUMENTS, argumentsOf(block.get(AiPayloadFields.INPUT)));
            items.add(item);
        }
        return items;
    }

    /**
     * content 中是否含工具调用块（响应侧判定「有没有必要写 tool_calls」）。
     */
    public static boolean hasToolUse(Object content) {
        return !toolCallsOf(content).isEmpty();
    }

    // ============================ 内部 ============================

    private static Map<String, Object> anthropicTool(Map<String, Object> function) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put(AiPayloadFields.NAME, PayloadMapSupport.text(function, AiPayloadFields.NAME));
        copyIfPresent(function, tool, AiPayloadFields.DESCRIPTION);
        // {@code parameters} 是 OpenAI 键、{@code input_schema} 是 Anthropic 键：
        // 两者都认，使本方法对「已是 Anthropic 形状」的输入幂等
        Object schema = function.get(AiPayloadFields.PARAMETERS) == null
                ? function.get(AiPayloadFields.INPUT_SCHEMA) : function.get(AiPayloadFields.PARAMETERS);
        if (schema != null) {
            tool.put(AiPayloadFields.INPUT_SCHEMA, schema);
        }
        return tool;
    }

    /** 函数体：chat 形状下钻 {@code function}，Responses 的扁平形状视自身为函数体。 */
    private static Map<String, Object> functionBody(Map<String, Object> tool) {
        return tool.get(AiPayloadFields.FUNCTION) instanceof Map<?, ?> raw
                ? PayloadMapSupport.cast(raw) : tool;
    }

    private static boolean isToolResultMessage(Map<String, Object> message) {
        return AiPayloadFields.TOOL.equals(message.get(AiPayloadFields.ROLE))
                && message.get(AiPayloadFields.TOOL_CALL_ID) != null;
    }

    private static Map<String, Object> toolResultBlock(Map<String, Object> message) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put(AiPayloadFields.TYPE, AiPayloadFields.TOOL_RESULT);
        block.put(AiPayloadFields.TOOL_USE_ID, message.get(AiPayloadFields.TOOL_CALL_ID));
        block.put(AiPayloadFields.CONTENT, message.get(AiPayloadFields.CONTENT) == null
                ? "" : message.get(AiPayloadFields.CONTENT));
        return block;
    }

    /** 连续的 tool 结果合并进同一条 user 消息（Anthropic 的 tool_use/tool_result 必须成对相邻）。 */
    private static void flushToolResults(List<Map<String, Object>> pending, List<Map<String, Object>> target) {
        if (pending.isEmpty()) {
            return;
        }
        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put(AiPayloadFields.ROLE, AiPayloadFields.USER);
        userMessage.put(AiPayloadFields.CONTENT, List.copyOf(pending));
        target.add(userMessage);
        pending.clear();
    }

    /** assistant 的 {@code tool_calls} → {@code content} 块数组（文本块在前，工具块在后）。 */
    private static Map<String, Object> rewriteAssistant(Map<String, Object> message) {
        if (!(message.get(AiPayloadFields.TOOL_CALLS) instanceof List<?> calls) || calls.isEmpty()) {
            return message;
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        String text = PayloadMapSupport.text(message, AiPayloadFields.CONTENT);
        if (text != null) {
            blocks.add(Map.of(AiPayloadFields.TYPE, TEXT, AiPayloadFields.TEXT, text));
        }
        blocks.addAll(toolUseBlocksOf(calls));

        Map<String, Object> rewritten = new LinkedHashMap<>(message);
        rewritten.put(AiPayloadFields.CONTENT, blocks);
        rewritten.remove(AiPayloadFields.TOOL_CALLS);
        rewritten.remove(AiPayloadFields.TOOL_CALL_ID);
        return rewritten;
    }

    /** Anthropic content 块是否全为已知类型（text / tool_use / tool_result）。 */
    private static boolean recognizable(List<?> blocks) {
        for (Object item : blocks) {
            if (!(item instanceof Map<?, ?> raw)) {
                return false;
            }
            Object type = PayloadMapSupport.cast(raw).get(AiPayloadFields.TYPE);
            if (!TEXT.equals(type)
                    && !AiPayloadFields.TOOL_USE.equals(type)
                    && !AiPayloadFields.TOOL_RESULT.equals(type)) {
                return false;
            }
        }
        return true;
    }

    /** 单条 Anthropic 消息按块拆成 OpenAI 消息：文本归一条，每个 tool_result 各成一条 tool 消息。 */
    private static List<Map<String, Object>> splitBlocks(Map<String, Object> message, List<?> blocks) {
        String role = PayloadMapSupport.text(message, AiPayloadFields.ROLE);
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        List<Map<String, Object>> toolMessages = new ArrayList<>();
        for (Object item : blocks) {
            Map<String, Object> block = PayloadMapSupport.cast((Map<?, ?>) item);
            Object type = block.get(AiPayloadFields.TYPE);
            if (TEXT.equals(type)) {
                text.append(PayloadMapSupport.stringOf(block.get(AiPayloadFields.TEXT)));
            } else if (AiPayloadFields.TOOL_USE.equals(type)) {
                toolCalls.addAll(toolCallsOf(List.of(block)));
            } else if (AiPayloadFields.TOOL_RESULT.equals(type)) {
                Map<String, Object> toolMessage = new LinkedHashMap<>();
                toolMessage.put(AiPayloadFields.ROLE, AiPayloadFields.TOOL);
                toolMessage.put(AiPayloadFields.TOOL_CALL_ID, block.get(AiPayloadFields.TOOL_USE_ID));
                toolMessage.put(AiPayloadFields.CONTENT, block.get(AiPayloadFields.CONTENT));
                toolMessages.add(toolMessage);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        if (!toolCalls.isEmpty()) {
            Map<String, Object> assistant = new LinkedHashMap<>(message);
            assistant.put(AiPayloadFields.CONTENT, text.isEmpty() ? null : text.toString());
            assistant.put(AiPayloadFields.TOOL_CALLS, toolCalls);
            result.add(assistant);
        } else if (role != null && !AiPayloadFields.TOOL.equals(role)
                && (!text.isEmpty() || toolMessages.isEmpty())) {
            // 纯 tool_result 的 user 消息不应再产一条空文本 user —— 那会让客户端看到
            // 「空 user 消息 + tool 消息」两条，Anthropic 侧只发了一条
            Map<String, Object> plain = new LinkedHashMap<>(message);
            plain.put(AiPayloadFields.CONTENT, text.toString());
            result.add(plain);
        }
        result.addAll(toolMessages);
        return result;
    }

    private static Object namedToolChoice(String name) {
        if (name == null) {
            return null;
        }
        Map<String, Object> function = new LinkedHashMap<>();
        function.put(AiPayloadFields.NAME, name);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put(AiPayloadFields.TYPE, AiPayloadFields.FUNCTION);
        choice.put(AiPayloadFields.FUNCTION, function);
        return choice;
    }

    /** Anthropic {@code input} 对象 → OpenAI {@code arguments} JSON 文本；缺失退化 {@code "{}"}。 */
    private static String argumentsOf(Object input) {
        if (input == null) {
            return EMPTY_ARGUMENTS;
        }
        String json = JsonKit.toJson(input);
        return json == null ? EMPTY_ARGUMENTS : json;
    }

    /** OpenAI {@code arguments} JSON 文本 → Anthropic {@code input} 对象；解析失败退化空对象。 */
    private static Object inputOf(Object arguments) {
        if (arguments instanceof Map<?, ?>) {
            return arguments;
        }
        if (!(arguments instanceof String text) || text.isBlank()) {
            return Map.of();
        }
        Map<String, Object> parsed = JsonKit.fromJson(text, new TypeReference<>() {
        });
        return parsed == null ? Map.of() : parsed;
    }

    private static List<Map<String, Object>> blocksOf(Object content) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (!(content instanceof List<?> list)) {
            return blocks;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> raw) {
                blocks.add(PayloadMapSupport.cast(raw));
            }
        }
        return blocks;
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }
}
