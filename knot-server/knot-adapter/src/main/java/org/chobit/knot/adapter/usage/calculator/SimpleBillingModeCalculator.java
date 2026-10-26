package org.chobit.knot.adapter.usage.calculator;

import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.AudioBillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.NormalizedBillingAmount;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.NormalizedUsageContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SimpleBillingModeCalculator extends AbstractBillingModeCalculator {

    @Override
    public BillingModeEnum mode() {
        return BillingModeEnum.CUSTOM;
    }

    @Override
    public NormalizedBillingAmount calculate(NormalizedUsageContext context) {
        BillingRuleRef rule = rule(context);
        BillingUsage usage = context.usage();
        BillingModeEnum mode = resolveMode(rule);
        long amount = billingAmount(usage, mode, rule);
        int unitSize = unitSize(rule, mode);
        BillingConfig config = BillingConfig.fromJsonOrNull(rule.configJson());
        BigDecimal unitPrice = config == null
                ? BigDecimal.ZERO
                : config.pricingPlan(PricingPlanEnum.fromCode(rule.pricingPlan()))
                        .resolveDefaultPrice(new BillingConfig.PricingContext(amount, context.occurredAt()), BigDecimal.ZERO);
        BigDecimal totalCost = cost(amount, unitPrice, unitSize);
        return NormalizedBillingAmount.of(
                usage.tokenUsage().totalTokens(),
                totalCost,
                List.of(detail(normalizeDetailType(rule.billingMode(), mode), amount, totalCost, unitPrice, unitSize))
        );
    }

    private long billingAmount(BillingUsage usage, BillingModeEnum mode) {
        return billingAmount(usage, mode, null);
    }

    /**
     * 按计费模式取计费量：token 类模式取 token 维度，媒体模式取各自的计量
     * （IMAGE 张数 / AUDIO、VIDEO 秒数），类型即契约。
     *
     * <p>⚠ VIDEO 的量纲由<b>规则单位</b>声明：选了 {@code 1M_TOKENS} 等 token 单位时取 token 量
     * （豆包 Seedance「元/百万token」），否则取秒。token 取数与 {@code VideoBillingModeCalculator}
     * 共用 {@code AbstractBillingModeCalculator} 上的同一份实现，两条路径（绑定 VIDEO 提取器 /
     * 走通用兜底）不会漂移——换一次提取器绑定就换一套算法是最难察觉的资损。</p>
     *
     * <p>⚠ 已知边界：本类只解析<b>默认价</b>，不做分辨率 / 尺寸分档（那是模式专属计算器的职责）。
     * 即同一条配了 {@code resolutionPrices} 的 VIDEO 规则，走通用兜底会按 {@code defaultUnitPrice}
     * 出账。这是既有行为，靠「视频模型绑定 VIDEO 提取器」保证走对路径，不在此处复制一份维度逻辑。</p>
     */
    private long billingAmount(BillingUsage usage, BillingModeEnum mode, BillingRuleRef rule) {
        if (mode == BillingModeEnum.VIDEO && tokenQuantity(rule, mode)) {
            // 走与 VideoBillingModeCalculator 同一份 token 取数（总量 -> 输出量），
            // 不能落到下面的通用 tokenAmount(usage, false)：那条链在总量缺失时回落**输入**量，
            // 视频模型只上报输出 token 时会静默算出 0 金额。
            return tokenAmount(usage.tokenUsage());
        }
        return switch (mode) {
            case EMBEDDING -> tokenAmount(usage, true);
            case REQUEST -> 1L;
            case IMAGE -> atLeastOne(imageCount(usage));
            case AUDIO, VIDEO -> atLeastOne(mediaSeconds(usage));
            default -> tokenAmount(usage, false);
        };
    }

    /** token 用量取数：媒体记录经内嵌部分取，非 token 来源为 0 */
    private long tokenAmount(BillingUsage usage, boolean inputFirst) {
        TokenBillingUsage token = usage.tokenUsage();
        return inputFirst
                ? firstAmount(token.inputTokens(), token.totalTokens())
                : firstAmount(token.totalTokens(), token.inputTokens());
    }

    private long imageCount(BillingUsage usage) {
        return usage instanceof ImageBillingUsage image ? image.count() : 0L;
    }

    /**
     * 媒体模式的秒数：媒体记录取各自时长；token 用量与图像记录无时长口径，为 0
     * （由 {@link #atLeastOne} 兜底成 1）。
     */
    private long mediaSeconds(BillingUsage usage) {
        if (usage instanceof VideoBillingUsage video) {
            return video.durationSeconds();
        }
        if (usage instanceof AudioBillingUsage audio) {
            return audio.durationSeconds();
        }
        return 0L;
    }

    private long atLeastOne(long amount) {
        return amount > 0 ? amount : 1L;
    }

    private BillingModeEnum resolveMode(BillingRuleRef rule) {
        BillingModeEnum modeEnum = BillingModeEnum.fromCode(rule == null ? null : rule.billingMode());
        return modeEnum == null ? BillingModeEnum.CUSTOM : modeEnum;
    }
}
