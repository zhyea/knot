package org.chobit.knot.adapter.usage.extractor;

import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.usage.UsageExtractItem;
import org.chobit.knot.adapter.usage.ModelUsageNormalizer;
import org.chobit.knot.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.adapter.usage.calculator.TokenBillingModeCalculator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 容忍空用量的 token 提取器：取量口径与 {@link DefaultTokenUsageExtractor} <b>完全一致</b>
 * （同一出口 {@code TokenBillingUsage.fromResponse}），唯一差别是显式声明
 * {@link #allowsEmptyUsage()} 为 {@code true}。
 *
 * <p>适用对象：上游 {@code usage} 里会带本提取器不消费的正数字段，导致 token 分项被读成全零的供应商。
 * 典型如只回了 {@code completion_tokens_details.reasoning_tokens} 或媒体时长、
 * 而 {@code prompt_tokens} / {@code completion_tokens} 均为 0 的响应。
 * 这类响应在默认提取器下会被判成「口径错配」并让请求失败，绑到本提取器即放行。</p>
 *
 * <p><b>这是显式配置，不是兜底</b>：它不解析新协议、不猜测字段语义，只向注册表声明
 * 「我这个模型允许全零」。是否真的缺量，仍可由 WARN 日志与对账侧观察。</p>
 */
@Component
@Order(1)
public class EmptyAllowedTokenUsageExtractor implements UsageExtractor<TokenBillingUsage> {

    public static final String CODE = "TOKEN_ALLOW_EMPTY";

    private final TokenBillingModeCalculator calculator;

    public EmptyAllowedTokenUsageExtractor(TokenBillingModeCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Token Usage Extractor (empty allowed)";
    }

    @Override
    public String description() {
        return "容忍空用量的通用 token 提取器：取量口径与 DEFAULT-TOKEN 完全相同（按 OpenAI 口径读 "
            + "输入、输出、缓存读写等 token 分项），差别只在显式声明「允许提取结果为全零」。"
            + "用于上游 usage 含本提取器不消费字段（如 reasoning_tokens、媒体时长）而 token 分项为 0 的供应商——"
            + "绑定后这类响应按合法零用量放行（记 WARN），不再被判为口径错配而让请求失败。"
            + "注意：这是显式配置而非兜底，不能替代为 camelCase 等非 OpenAI 口径绑定专用提取器。";
    }

    @Override
    public boolean allowsEmptyUsage() {
        return true;
    }

    @Override
    public BillingModeCalculator calculator() {
        return calculator;
    }

    @Override
    public Set<BillingModeEnum> billingModes() {
        // 与 DEFAULT 同为通用口径：Simple 计算器按规则模式分摄取量，全部模式均可服务
        return EnumSet.allOf(BillingModeEnum.class);
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> empty() {
        return UsageExtractItem.of(TokenBillingUsage.empty());
    }

    @Override
    public UsageExtractItem<TokenBillingUsage> extractUsage(Map<String, Object> body, Map<String, Object> requestBody) {
        TokenBillingUsage billing = TokenBillingUsage.fromResponse(body);
        return UsageExtractItem.of(billing, ModelUsageNormalizer.fromBilling(billing, body));
    }
}
