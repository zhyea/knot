package org.chobit.knot.gateway.constants;

/**
 * Executes the public operation. Executes the public operation.
 */
public final class AiPayloadFields {

    public static final String MODEL = "model";
    public static final String MESSAGES = "messages";
    public static final String ROLE = "role";
    public static final String CONTENT = "content";
    public static final String SYSTEM = "system";
    public static final String USER = "user";
    public static final String INPUT = "input";
    public static final String INSTRUCTIONS = "instructions";
    public static final String PROMPT = "prompt";
    public static final String USAGE = "usage";
    /**
     * Responses 协议SSE事件（response.completed / response.incomplete 等）把完整响应包在
     * {@code response} 子对象里，{@code usage} 随之嵌套在 {@code response.usage}。
     */
    public static final String RESPONSE = "response";
    public static final String STOP = "stop";
    public static final String STOP_SEQUENCES = "stop_sequences";

    /** 工具定义数组：OpenAI 为 {@code [{type,function:{...}}]}，Anthropic 为 {@code [{name,description,input_schema}]}。 */
    public static final String TOOLS = "tools";
    /** OpenAI 工具选择的字符串档位（{@code auto/none/required}）；对象档位见 {@link #TOOL_CHOICE_FUNCTION}。 */
    public static final String TOOL_CHOICE = "tool_choice";
    /** OpenAI {@code tool_choice} 的对象档位：{@code {type:function, function:{name}}}，Anthropic 对应 {@code {type:tool,name}}。 */
    public static final String TOOL_CHOICE_FUNCTION = "function";
    /** OpenAI 函数式工具的外壳名，也是 {@code tool_choice} 内层包装名。 */
    public static final String FUNCTION = "function";
    /** OpenAI assistant 消息上的工具调用数组。 */
    public static final String TOOL_CALLS = "tool_calls";
    /** OpenAI 回传工具结果的消息角色与配套 id 字段。 */
    public static final String TOOL = "tool";
    public static final String TOOL_CALL_ID = "tool_call_id";
    /** Anthropic content block 类型：工具调用块 / 工具结果块。 */
    public static final String TOOL_USE = "tool_use";
    public static final String TOOL_RESULT = "tool_result";
    public static final String TOOL_USE_ID = "tool_use_id";
    /** Anthropic 工具参数 JSON Schema 的键名（对应 OpenAI 的 {@link #PARAMETERS}）。 */
    public static final String INPUT_SCHEMA = "input_schema";
    /** OpenAI 工具参数 JSON Schema 的键名。 */
    public static final String PARAMETERS = "parameters";
    /** Responses 协议里工具调用的 output 项类型。 */
    public static final String FUNCTION_CALL = "function_call";
    /** OpenAI 工具调用的 JSON 参数字符串（流式时逐片到达）。 */
    public static final String ARGUMENTS = "arguments";

    /** 工具名 / 工具描述（两协议同名，收在此处避免各适配器散落字面量）。 */
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String MAX_TOKENS = "max_tokens";
    public static final String MAX_COMPLETION_TOKENS = "max_completion_tokens";
    public static final String MAX_OUTPUT_TOKENS = "max_output_tokens";
    public static final String TOTAL_TOKENS = "total_tokens";
    public static final String PROMPT_TOKENS = "prompt_tokens";
    public static final String INPUT_TOKENS = "input_tokens";
    public static final String COMPLETION_TOKENS = "completion_tokens";
    public static final String OUTPUT_TOKENS = "output_tokens";
    /**
     * 网关向调用方输出的扩展信息字段名，值为 {@code {usage, billing}} 两视图。
     *
     * <p>仅在路由消费者 {@code return_usage_detail} 打开时注入：整包响应作为响应体字段，
     * SSE 响应作为独立的 {@code data:} 事件插在 {@code [DONE]} 之前（无 [DONE] 时追加在流末尾），
     * 不改动上游原有 chunk。历史字段 {@code knot_usage} / {@code model_usage}
     * 已被本字段取代（一次做干净，不留兼容）。</p>
     *
     * <p>{@code billing} 只在命中计费规则时出现；无规则可命中时整个 {@code billing} 省略，
     * 使调用方能区分「免费」与「未配置计费」。</p>
     */
    public static final String KNOT_EXTEND = "knot_extend";
    /**
     * 上游原始 {@code usage} 原文透传字段名，与 {@link #KNOT_EXTEND} 并列注入
     * （整包为响应体字段，SSE 为同一独立事件的并列字段），仅供下游与归一化结果对照排障，
     * 业务口径仍以 {@code knot_extend} 的 {@code usage} / {@code billing} 为准。
     * 字段名沿用上游通用的 {@code "usage"}：整包模式下与上游响应自带的 {@code usage}
     * 同义覆盖（值相同），下游按既有键名即可读到原始用量。上游未上报 {@code usage} 时省略。
     */
    public static final String RAW_USAGE = "usage";
    public static final String STREAM = "stream";

    /** 载荷类型判别字段：Anthropic 事件 {@code type}、Responses output 项 {@code type} 共用。 */
    public static final String TYPE = "type";
    /** 载荷标识字段：消息 / 事件 / 工具调用 id。 */
    public static final String ID = "id";
    /** 流式增量字段（OpenAI chunk 与 Anthropic 事件同名）。 */
    public static final String DELTA = "delta";
    /** 候选序号（OpenAI choices 与 Anthropic content block 同名）。 */
    public static final String INDEX = "index";
    /** 文本载体：Anthropic text 块类型与其 {@code text} 字段同名（OpenAI 裸串侧无此键）。 */
    public static final String TEXT = "text";

    private AiPayloadFields() {
    }
}
