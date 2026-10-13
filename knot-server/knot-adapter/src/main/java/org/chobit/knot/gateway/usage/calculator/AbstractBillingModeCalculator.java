package org.chobit.knot.gateway.usage.calculator;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.model.TokenUsageDetailKind;
import org.chobit.knot.gateway.usage.NormalizedUsageContext;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class AbstractBillingModeCalculator implements BillingModeCalculator {

    protected BillingRuleEntity rule(NormalizedUsageContext context) {
        return context.rule();
    }

    protected int unitSize(BillingRuleEntity rule) {
        return BillingUnitEnum.unitSizeOf(normalizeUnit(rule.getUnit()));
    }

    protected BigDecimal safeMoney(BigDecimal value) {
        return ObjectUtils.defaultIfNull(value, BigDecimal.ZERO);
    }

    protected BigDecimal cost(long amount, BigDecimal unitPrice, int unitSize) {
        if (amount <= 0 || unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(amount)
                .multiply(unitPrice)
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), 8, RoundingMode.HALF_UP);
    }

    protected NormalizedUsageDetail detail(String type,
                                           long tokens,
                                           BigDecimal cost,
                                           BigDecimal unitPrice,
                                           int unitSize) {
        return new NormalizedUsageDetail(
                type,
                tokens,
                plainMoney(safeMoney(cost)),
                pricePerMillion(safeMoney(unitPrice), unitSize)
        );
    }

    /**
     * 金额的对外文本形态：十进制普通串（{@link BigDecimal#toPlainString()}），绝不出科学计数法。
     *
     * <p>detail.cost 的契约是字符串：Jackson 默认按 {@link BigDecimal#toString()} 写数值，
     * 调整指数 &lt; -6 的小额（如 0.00000001）会序列化成 {@code 1E-8}；在进入
     * {@link NormalizedUsageDetail} 前统一收敛成普通十进制。</p>
     */
    protected static String plainMoney(BigDecimal value) {
        return value.toPlainString();
    }

    /** 枚举版重载：明细类型以 {@link TokenUsageDetailKind} 声明，避免调用处散落魔法串 */
    protected NormalizedUsageDetail detail(TokenUsageDetailKind kind,
                                           long tokens,
                                           BigDecimal cost,
                                           BigDecimal unitPrice,
                                           int unitSize) {
        return detail(kind.key(), tokens, cost, unitPrice, unitSize);
    }

    protected BigDecimal pricePerMillion(BigDecimal unitPrice, int unitSize) {
        if (unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        }
        return unitPrice
                .multiply(BigDecimal.valueOf(1_000_000L))
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), 8, RoundingMode.HALF_UP);
    }

    protected String normalizeDetailType(String itemType, BillingModeEnum mode) {
        if (StringUtils.isNotBlank(itemType)) {
            return StringUtils.trim(itemType);
        }
        return mode == null ? BillingModeEnum.CUSTOM.code() : mode.code();
    }

    protected String normalizeUnit(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trim(value));
        return StringUtils.defaultIfBlank(normalized, BillingUnitEnum.ONE_K_TOKENS.code());
    }

    protected long firstAmount(long primary, long fallback) {
        return primary > 0 ? primary : fallback;
    }
}
