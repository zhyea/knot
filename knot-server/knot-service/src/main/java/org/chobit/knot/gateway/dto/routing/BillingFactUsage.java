package org.chobit.knot.gateway.dto.routing;

/**
 * 影子重算的用量视图（提取器产出，未计价）：token 形态与媒体形态统一落在一份结构。
 *
 * <p>媒体记录内嵌 token 用量（如 gpt-image-1 的 {@code usage.total_tokens}），
 * 故 token 分量始终透出；{@code genericAmount} 仅对媒体模式有意义（图片张数 / 音视频秒数）。</p>
 *
 * @param tokenBased    是否以 token 为主计量（TOKEN 模式提取器产出）
 * @param inputTokens   输入 token
 * @param outputTokens  输出 token
 * @param totalTokens   总量（上游未给时由提取器补齐为 input+output）
 * @param cacheReadTokens  缓存读 token
 * @param cacheWriteTokens 缓存写 token
 * @param genericAmount 媒体主计量（图片张数 / 音视频秒数；token 模式为 0）
 */
public record BillingFactUsage(boolean tokenBased,
                               long inputTokens,
                               long outputTokens,
                               long totalTokens,
                               long cacheReadTokens,
                               long cacheWriteTokens,
                               long genericAmount) {

    /** 媒体场景下内嵌 token 之和（无内嵌则为 0） */
    public long embeddedTokenSum() {
        return inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens;
    }
}
