package org.chobit.knot.adapter.usage;

import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;

/**
 * 一次上游用量提取的双视图结果：计费输入（强类型 sealed）+ 对外统一用量视图。
 * 提取器解析协议字段时同时产出 {@link #billing()} 与 {@link #usage()}，输出层直接消费。
 * {@code billing} 恒非空，无用量时用对应模式的 {@code empty()}；{@code usage} 可为 {@code null}。
 * 不放原始 JSON，原始 usage 由 {@code UsageAccounting.rawUsage/rawBody} 保留。
 *
 * @param billing 计费输入（{@code T extends BillingUsage}）
 * @param usage   对外统一用量视图，可为 {@code null}
 */
public record UsageExtractItem<T extends BillingUsage>(T billing, ModelUsage usage) {

    /** 只有计费视图、无可归一化用量。 */
    public static <T extends BillingUsage> UsageExtractItem<T> of(T billing) {
        return new UsageExtractItem<>(billing, null);
    }

    /** 双视图齐全。 */
    public static <T extends BillingUsage> UsageExtractItem<T> of(T billing, ModelUsage usage) {
        return new UsageExtractItem<>(billing, usage);
    }

    /** 计费视图与展示视图都没有可用量。 */
    public boolean isEmpty() {
        return (billing == null || billing.isEmpty()) && usage == null;
    }

    /**
     * 本次提取的 token 总量：计费视图优先，缺失时退回用量视图。
     * 用于 SSE 逐事件挑选「信息量最大」的一份，与 {@code UsageAccounting.totalTokens()} 口径一致。
     */
    public long totalTokens() {
        if (billing != null) {
            TokenBillingUsage token = billing.tokenUsage();
            if (token != null) {
                long total = token.totalTokens();
                if (total <= 0) {
                    total = token.inputTokens() + token.outputTokens();
                }
                if (total > 0) {
                    return total;
                }
            }
        }
        return usage == null ? 0L : usage.totalTokens();
    }

    /**
     * SSE 逐事件合并：{@code billing} 与 {@code usage} 都按分量取最大 / 取先出现的非空值，合并规则见 {@link ModelUsage#mergeMax}。
     * 同一提取器跨事件产出的 {@code T} 类型必然一致，故 {@code billing.mergeMax} 的返回值可安全还原为 {@code T}。
     */
    @SuppressWarnings("unchecked")
    public UsageExtractItem<T> mergeMax(UsageExtractItem<T> other) {
        if (other == null) {
            return this;
        }
        T mergedBilling = billing;
        if (billing == null) {
            mergedBilling = other.billing;
        } else if (other.billing != null) {
            mergedBilling = (T) billing.mergeMax(other.billing);
        }

        ModelUsage mergedUsage = usage == null ? other.usage
                : other.usage == null ? usage : usage.mergeMax(other.usage);
        return new UsageExtractItem<>(mergedBilling, mergedUsage);
    }
}
