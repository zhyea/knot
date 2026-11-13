package org.chobit.knot.adapter.usage.calculator;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.BillingUnitEnum;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.TokenUsageDetailKind;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.BillingConfig;

import java.math.BigDecimal;
import java.math.MathContext;

public abstract class AbstractBillingModeCalculator implements BillingModeCalculator {

    /**
     * 金额除法的精度兜底（128 位十进制）：单位基数出现非 10 的幂时除法可能不终止，
     * 无精度上下文的 {@code divide} 会抛 {@link ArithmeticException}。
     * 按分钟计费必须在配置期把单价折成每秒价，不允许 60 进金额公式。
     */
    protected static final MathContext MONEY_DIVISION = MathContext.DECIMAL128;

    protected BillingRuleRef rule(NormalizedUsageContext context) {
        return context.rule();
    }

    /**
     * 金额公式的分母：单位基数，一律经 {@link BillingModeEnum#normalizeUnit(String)} 归一。
     * 各计算器必须传自己的 {@link BillingModeEnum#mode()}，不用规则列的空值兜底。
     */
    /**
     * 量纲唯一出口：归一后的计费单位编码。明细行的 {@code unit} 一律从这里取，
     * 不与 {@link #unitSize} 各推一份。
     */
    protected String unitCode(BillingRuleRef rule, BillingModeEnum mode) {
        return mode.normalizeUnit(rule == null ? null : rule.unit());
    }

    protected int unitSize(BillingRuleRef rule, BillingModeEnum mode) {
        return BillingUnitEnum.unitSizeOf(unitCode(rule, mode));
    }

    protected BigDecimal safeMoney(BigDecimal value) {
        return ObjectUtils.defaultIfNull(value, BigDecimal.ZERO);
    }

    /**
     * 单项金额 = 用量 × 单价 ÷ 单位基数，结果不定标小数位数。
     */
    protected BigDecimal cost(long amount, BigDecimal unitPrice, int unitSize) {
        if (amount <= 0 || unitPrice == null || unitPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amount)
                .multiply(unitPrice)
                .divide(BigDecimal.valueOf(Math.max(1, unitSize)), MONEY_DIVISION);
    }

    /**
     * 明细行唯一构造出口：量与单位必须同行给出，{@code unitSize} 由 {@code unitCode} 派生，
     * 调用处不传裸基数。{@code price} 是规则配置的原始单价，基准由同行 {@code unit} 声明
     * （{@code PER_SECOND} = 每秒价、{@code 1M_TOKENS} = 每百万 Token 价），
     * 复算式 {@code cost = quantity × price ÷ unitSize(unit)}，契约里不另发 {@code unit_size}。
     */
    protected NormalizedUsageDetail detail(String type,
                                           long quantity,
                                           BigDecimal cost,
                                           BigDecimal unitPrice,
                                           String unitCode) {
        return new NormalizedUsageDetail(
                type,
                quantity,
                unitCode,
                NormalizedBillingAmount.plainMoney(safeMoney(cost)),
                NormalizedBillingAmount.plainMoney(safeMoney(unitPrice))
        );
    }

    /** 枚举版重载：明细类型以 {@link TokenUsageDetailKind} 声明，避免调用处散落魔法串 */
    protected NormalizedUsageDetail detail(TokenUsageDetailKind kind,
                                           long quantity,
                                           BigDecimal cost,
                                           BigDecimal unitPrice,
                                           String unitCode) {
        return detail(kind.key(), quantity, cost, unitPrice, unitCode);
    }

    protected String normalizeDetailType(String itemType, BillingModeEnum mode) {
        if (StringUtils.isNotBlank(itemType)) {
            return StringUtils.trim(itemType);
        }
        return mode == null ? BillingModeEnum.CUSTOM.code() : mode.code();
    }

    protected long firstAmount(long primary, long fallback) {
        return primary > 0 ? primary : fallback;
    }

    /**
     * 本次是否按 token 计量：由规则单位声明（{@link BillingUnitEnum#tokenBased()}）。
     * 单位先过 {@link BillingModeEnum#normalizeUnit(String)}，空白 / 非法 / 本模式不支持时取模式默认
     * （VIDEO → {@code PER_SECOND}）；所有计算器共用本判断。
     */
    protected boolean tokenQuantity(BillingRuleRef rule, BillingModeEnum mode) {
        String unitCode = mode == null ? null : mode.normalizeUnit(rule == null ? null : rule.unit());
        BillingUnitEnum unit = BillingUnitEnum.fromCode(unitCode);
        return unit != null && unit.tokenBased();
    }

    /**
     * token 计费量：总量优先，缺失时回落输出量（不回落输入量），与
     * {@code TokenBillingModeCalculator} 同口径；不按视频模型上报的 token 语义做启发式猜测。
     */
    protected long tokenAmount(TokenBillingUsage tokens) {
        return firstAmount(tokens.totalTokens(), tokens.outputTokens());
    }

    /**
     * 规则定价配置解析：非法 {@code config_json} 一律抛 {@link BusinessException}，不静默按 0 出账；
     * 空 / 空白返回 {@code null}（「未配置价格」是合法的规则状态）。
     */
    protected static BillingConfig configOf(BillingRuleRef rule) {
        String json = rule == null ? null : rule.configJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return BillingConfig.parse(json);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "计费规则 config_json 非法，已中止计费: " + e.getMessage());
        }
    }
}
