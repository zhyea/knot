package org.chobit.knot.gateway.usage.extractor;

import org.chobit.knot.gateway.adapter.aws.BedrockConverseUsageSupport;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.usage.ModelUsageNormalizer;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.calculator.TokenBillingModeCalculator;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * AWS Bedrock Converse 用量提取器：Converse 的 {@code usage} 是 camelCase
 * （{@code inputTokens} / {@code outputTokens} / {@code cacheReadInputTokens} / {@code cacheWriteInputTokens}），
 * 通用的 {@code TokenBillingUsage}（只认 snake_case）读不出来，故单独收口。
 *
 * <p>Anthropic 模型若走 InvokeModel 绑定，响应是 Anthropic 原生形状，
 * 直接用既有的 {@code ANTHROPIC} 提取器即可，无需本类。</p>
 */
@Component
public class BedrockConverseUsageExtractor implements UsageExtractor<TokenBillingUsage> {

    public static final String CODE = "BEDROCK_CONVERSE";

    private final TokenBillingModeCalculator calculator;

    public BedrockConverseUsageExtractor(TokenBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "AWS Bedrock Converse Usage Extractor";
    }

    @Override
    public String description() {
        return "AWS Bedrock Converse 专用提取器：按 Converse 的 camelCase 口径提取 inputTokens / outputTokens "
                + "与 cacheRead/cacheWrite 明细。待实测确认（R2）：Converse 的 inputTokens 是否已含缓存读写——"
                + "当前按「不含」处理，原样上报，不加回输入侧总量。仅服务 TOKEN 计费模式。";
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        return EnumSet.of(BillingModeEnum.TOKEN);
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> empty() {
        return UsageExtractItem.of(TokenBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage billing = BedrockConverseUsageSupport.fromResponse(body);
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }

    /**
     * P1 不支持流式：Converse 流式是 AWS EventStream 二进制帧，usage 落在 metadata 事件里，
     * 需要独立解码器（P2）。
     */
    @Override
    public boolean streamSupported() {
        return false;
    }
}
