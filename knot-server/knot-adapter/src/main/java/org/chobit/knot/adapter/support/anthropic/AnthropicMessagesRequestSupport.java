package org.chobit.knot.adapter.support.anthropic;

import org.chobit.knot.common.constants.AiPayloadFields;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 特有的请求字段兜底与就地改写：{@code max_tokens} 缺省兜底、工具字段换形。
 * 服务 {@code ChatCompletionsToMessagesUpstreamAdapter}、{@code CompletionsToMessagesUpstreamAdapter}、
 * {@code ResponsesToMessagesUpstreamAdapter}；只做字段编排，通用字段搬迁归 {@code RequestFieldSupport}，
 * 工具形状转换归 {@link AnthropicToolSupport}。
 */
public final class AnthropicMessagesRequestSupport {

    private AnthropicMessagesRequestSupport() {
    }

    /** {@code max_tokens} 缺省兜底 1024：Anthropic 要求必填，已有值不覆盖。 */
    public static void ensureMaxTokens(Map<String, Object> body) {
        body.putIfAbsent(AiPayloadFields.MAX_TOKENS, 1024);
    }

    /**
     * 工具语义就地换形：{@code tools} / {@code tool_choice} 定义，以及消息载体
     * （assistant {@code tool_calls} → {@code tool_use} 块、{@code role:tool} →
     * {@code user} + {@code tool_result} 块）。形状不认识时保留原值，对已转换过的 body 幂等。
     */
    public static void applyToolShape(Map<String, Object> body) {
        Object tools = AnthropicToolSupport.toolsToAnthropic(body.get(AiPayloadFields.TOOLS));
        if (tools != null) {
            body.put(AiPayloadFields.TOOLS, tools);
        }
        Object toolChoice = AnthropicToolSupport.toolChoiceToAnthropic(body.get(AiPayloadFields.TOOL_CHOICE));
        if (toolChoice != null) {
            body.put(AiPayloadFields.TOOL_CHOICE, toolChoice);
        }
        if (body.get(AiPayloadFields.MESSAGES) instanceof List<?> messages) {
            body.put(AiPayloadFields.MESSAGES, AnthropicToolSupport.messagesToAnthropic(messages));
        }
    }
}
