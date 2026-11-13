package org.chobit.knot.adapter.model.usage;

/**
 * AUDIO 模式计费输入：时长（秒），按秒计价。{@code tokens} 内嵌上游可能同时上报的 token 用量，
 * TOKEN 类规则走它计费；纯音频响应为空用量。
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
