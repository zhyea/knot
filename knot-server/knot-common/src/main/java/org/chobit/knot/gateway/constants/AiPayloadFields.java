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
    public static final String STOP = "stop";
    public static final String STOP_SEQUENCES = "stop_sequences";
    public static final String MAX_TOKENS = "max_tokens";
    public static final String MAX_COMPLETION_TOKENS = "max_completion_tokens";
    public static final String MAX_OUTPUT_TOKENS = "max_output_tokens";
    public static final String TOTAL_TOKENS = "total_tokens";
    public static final String PROMPT_TOKENS = "prompt_tokens";
    public static final String INPUT_TOKENS = "input_tokens";
    public static final String COMPLETION_TOKENS = "completion_tokens";
    public static final String OUTPUT_TOKENS = "output_tokens";
    /**
     * 网关向调用方输出的统一用量字段名，值为 {@code {usage, raw, billing}} 三视图。
     *
     * <p>仅在路由消费者 {@code return_usage_detail} 打开时注入：整包响应作为响应体字段，
     * SSE 响应作为独立的 {@code data:} 事件。历史字段 {@code knot_usage} 已被本字段取代。</p>
     */
    public static final String MODEL_USAGE = "model_usage";
    public static final String STREAM = "stream";

    private AiPayloadFields() {
    }
}
