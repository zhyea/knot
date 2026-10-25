package org.chobit.knot.gateway.adapter.model.usage;

/**
 * AUDIO 模式计费输入：时长（秒）。
 *
 * <p>时长是主要计费量（按秒计价，PER_MINUTE 已于 2026-10-17 退役）；后续若出现按字符等维度再扩字段。</p>
 *
 * <p>{@code tokens} 内嵌上游可能同时上报的 token 用量（如转写类接口的
 * {@code usage.total_tokens}），TOKEN 类规则走它计费；纯音频响应为空用量。</p>
 */
public record AudioBillingUsage(TokenBillingUsage tokens,
                                long durationSeconds) implements BillingUsage {

    public AudioBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
    }

    public static AudioBillingUsage empty() {
        return new AudioBillingUsage(TokenBillingUsage.empty(), 0L);
    }

    @Override
    public TokenBillingUsage tokenUsage() {
        return tokens;
    }

    @Override
    public boolean isEmpty() {
        return tokens.isEmpty() && durationSeconds <= 0;
    }

    @Override
    public AudioBillingUsage mergeMax(BillingUsage other) {
        if (!(other instanceof AudioBillingUsage that)) {
            return this;
        }
        return new AudioBillingUsage(
                tokens.mergeMax(that.tokens()),
                Math.max(durationSeconds, that.durationSeconds)
        );
    }
}
