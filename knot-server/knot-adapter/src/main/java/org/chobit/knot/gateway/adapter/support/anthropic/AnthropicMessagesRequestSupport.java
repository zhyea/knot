package org.chobit.knot.gateway.adapter.support.anthropic;

import org.chobit.knot.gateway.constants.AiPayloadFields;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 特有的请求字段兜底与就地改写：{@code max_tokens} 缺省兜底、工具字段换形。
 *
 * <p>服务对象：{@code ChatCompletionsToMessagesUpstreamAdapter}、
 * {@code CompletionsToMessagesUpstreamAdapter}、{@code ResponsesToMessagesUpstreamAdapter}
 * 三个向 Messages 转换的适配器（反向的 MessagesToChatCompletions 不需要——它要去 OpenAI 形状）。</p>
 *
 * <p>⚠ 通用字段搬迁（{@code max_completion_tokens→max_tokens}、{@code stop→stop_sequences}）
 * 归 {@code adapter.support.RequestFieldSupport}；工具语义的具体形状知识归
 * {@link AnthropicToolSupport}——本类只做「把 body 里的哪几个字段交给谁」这一步编排，
 * 不重复实现任何形状转换（判例：{@code move} 曾住在这里，2026-10-25 拆出）。</p>
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
     * {@code user} + {@code tool_result} 块）。
     *
     * <p>形状不认识时各路都保留原值（{@link AnthropicToolSupport} 返回 {@code null} 即「别动」），
     * 保证本方法对已转换过的 body 幂等。</p>
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
