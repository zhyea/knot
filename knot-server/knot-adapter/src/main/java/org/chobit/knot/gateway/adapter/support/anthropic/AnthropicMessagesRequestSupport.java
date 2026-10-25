package org.chobit.knot.gateway.adapter.support.anthropic;

import org.chobit.knot.gateway.constants.AiPayloadFields;

import java.util.Map;

/**
 * Anthropic Messages 特有的请求字段兜底：{@code max_tokens} 缺省兜底 1024。
 *
 * <p>Anthropic 要求 {@code max_tokens} 必填，入协议没给时必须补一个，否则上游 400。
 * 服务对象：{@code ChatCompletionsToMessagesUpstreamAdapter}、
 * {@code CompletionsToMessagesUpstreamAdapter}、{@code ResponsesToMessagesUpstreamAdapter}
 * 三个向 Messages 转换的适配器（反向的 MessagesToChatCompletions 不需要）。</p>
 *
 * <p>⚠ 通用字段搬迁（{@code max_completion_tokens→max_tokens}、{@code stop→stop_sequences}）
 * 归 {@code adapter.support.RequestFieldSupport}——通用能力不住本类（判例：{@code move}
 * 曾住在这里，2026-10-25 拆出）。</p>
 */
public final class AnthropicMessagesRequestSupport {

    private AnthropicMessagesRequestSupport() {
    }

    /** {@code max_tokens} 缺省兜底 1024：Anthropic 要求必填，已有值不覆盖。 */
    public static void ensureMaxTokens(Map<String, Object> body) {
        body.putIfAbsent(AiPayloadFields.MAX_TOKENS, 1024);
    }
}
