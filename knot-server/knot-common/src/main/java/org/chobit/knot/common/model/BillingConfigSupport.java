package org.chobit.knot.common.model;

import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.model.BillingConfig.PhaseCondition;
import org.chobit.knot.common.model.BillingConfig.PhaseRule;
import org.chobit.knot.common.model.BillingConfig.PriceKind;
import org.chobit.knot.common.model.BillingConfig.PriceSet;
import org.chobit.knot.common.model.BillingConfig.Pricing;
import org.chobit.knot.common.model.BillingConfig.Surcharge;
import org.chobit.knot.common.model.BillingConfig.TierRule;
import org.chobit.knot.common.model.BillingConfig.VideoConfig;
import org.chobit.knot.common.model.BillingConfig.Window;
import org.chobit.knot.common.model.BillingConfig.WindowRange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link BillingConfig} 的校验链（12 条规则）。
 *
 * <p>⭐ <b>为什么从 BillingConfig 里拆出来</b>：拆分前该类同时承担数据契约、解析、定价方案实现、
 * 校验四个职责，1304 行。校验链是其中<b>抽象层级独立</b>的一块 —— 12 个无状态静态判定，
 * 只读 {@link BillingConfig} 的字段、彼此不共享状态。数据类只该回答「我是什么」，
 * 「我合不合法」是另一件事。</p>
 *
 * <p>⚠ <b>没有保留 {@code BillingConfig#validate()} 的一行委托</b>：那是纯转发壳
 * （单实现接口/单行委托都不抽），留着会让「校验在哪」这个问题有两个答案。
 * 调用方直接调这里。</p>
 *
 * <p>约束：本类<b>不持有任何状态</b>，全部入参传递；返回值统一是「null 表示合法，否则是错误描述」。</p>
 *
 * <p>⚠ <b>为什么留在 {@code common.model} 而不是搬到 {@code common.pricing}</b>：本类要调
 * {@link BillingConfig#blankToNull}、{@code BillingConfig.RATE_MODE_ABSOLUTE} 等<b>包私有</b>成员，
 * 换包就编译不过。别为了「校验器跟计费类放一起更好看」去改那些成员的可见性——那等于把
 * 只该在包内共享的实现细节提成对外契约。</p>
 */
public final class BillingConfigSupport {

    private static final String PHASE_PEAK = "PEAK";
    private static final String PHASE_OFF_PEAK = "OFF_PEAK";
    private static final Set<String> SUPPORTED_PHASES = Set.of(PHASE_PEAK, PHASE_OFF_PEAK);

    /** 节假日策略首期固定：节假日整日低峰，覆盖星期与时段 */
    private static final Set<String> SUPPORTED_HOLIDAY_POLICIES = Set.of("OFF_PEAK");

    /** 调休策略：整日低峰，或按所调休星期的高峰窗口判定 */
    private static final Set<String> SUPPORTED_MAKE_UP_POLICIES = Set.of("OFF_PEAK", "FOLLOW_WEEKDAY_WINDOWS");

    /** 维度分档键的两个维度后缀（大小写不敏感、顺序固定） */
    private static final String DIMENSION_REFERENCE = "REFERENCE";
    private static final String DIMENSION_AUDIO = "AUDIO";

    private static final Set<String> SUPPORTED_DIMENSION_SUFFIXES = Set.of(
            "",
            ":" + DIMENSION_AUDIO,
            ":" + DIMENSION_REFERENCE,
            ":" + DIMENSION_REFERENCE + ":" + DIMENSION_AUDIO);

    /** cacheWriteMode 白名单：standard=单字段 / ttl=5m+1h 分字段 */
    private static final Set<String> SUPPORTED_CACHE_WRITE_MODES = Set.of("standard", "ttl");

    /** tierMode 白名单：取值本身是 {@link BillingConfig} 的数据契约，这里只判「允许哪些」 */
    private static final Set<String> SUPPORTED_TIER_MODES = Set.of(
            BillingConfig.TIER_MODE_PER_REQUEST, BillingConfig.TIER_MODE_PROGRESSIVE);

    /**
     * 基础结构校验：返回 null 表示合法，否则返回错误描述。
     * 覆盖：价格非负（defaultUnitPrice / basePrices / resolutionPrices）、
     * tier 的 from 必填且非负、from&lt;=to、档位不重叠（开放档位必须是最后一个）。
     */
    public static String validate(BillingConfig config) {
        if (config.cacheWriteMode() != null && !SUPPORTED_CACHE_WRITE_MODES.contains(config.cacheWriteMode())) {
            return "cacheWriteMode must be standard or ttl";
        }
        if (config.defaultUnitPrice() != null && config.defaultUnitPrice().signum() < 0) {
            return "defaultUnitPrice cannot be negative";
        }
        if (config.basePrices() != null) {
            for (PriceKind kind : PriceKind.values()) {
                BigDecimal price = config.basePrices().valueOf(kind);
                if (price != null && price.signum() < 0) {
                    return "base price cannot be negative: " + BillingConfig.jsonName(kind);
                }
            }
        }
        if (config.resolutionPrices() != null) {
            for (Map.Entry<String, BigDecimal> entry : config.resolutionPrices().entrySet()) {
                if (entry.getValue() == null || entry.getValue().signum() < 0) {
                    return "resolution price cannot be negative: " + entry.getKey();
                }
            }
        }
        String videoError = validateVideo(config.video());
        if (videoError != null) {
            return videoError;
        }
        String tierError = validateTier(config.tier());
        if (tierError != null) {
            return tierError;
        }
        return validateSurcharges(config.surcharges());
    }

    /**
     * 方案级校验：按 pricingPlan 追加约束。
     * TIERED 要求 tier 非空且每项 unitPrices 完整保留全部价格字段（价格非负）；
     * tierMode 只属于 TIERED，其它方案配了它一律拒绝（见方法内注释）；
     * FIXED 不接受 {@code resolutionPrices}——档位表只属于维度分档价。
     */
    public static String validate(BillingConfig config, PricingPlanEnum plan) {
        PricingPlanEnum resolved = plan == null ? PricingPlanEnum.FIXED : plan;
        if (!resolved.isAvailable()) {
            return "pricing plan not supported yet: " + resolved.code();
        }
        String baseError = validate(config);
        if (baseError != null) {
            return baseError;
        }
        // config.tierMode() 只在 TIERED 下有意义：其它方案配了它说明配置意图与方案不一致，拒绝保存。
        // ⚠ 必须在下面几个分支之前判：DIMENSION_TIERED / VENDOR_MINIMAX / PEAK_OFF_PEAK 各自
        //   直接 return，写在末尾会被整体跳过，僵尸字段静默进 config_json（前端也只在 TIERED 下写它）。
        if (resolved != PricingPlanEnum.TIERED && BillingConfig.blankToNull(config.tierMode()) != null) {
            return "tierMode is only allowed for TIERED plan";
        }
        // 固定价不呈现档位（2026-10-30 定调）：档位表是「按请求属性分档」的数据，只归维度分档价。
        // 前端在固定价下已不渲染这张表，后端若仍放行，手工改 config_json 就能配出「界面上无处可见、
        // 取价却会命中」的价表（FixedPricePolicy 的维度命中对两者是同一份实现）。
        if (resolved == PricingPlanEnum.FIXED && config.resolutionPrices() != null && !config.resolutionPrices().isEmpty()) {
            return "FIXED plan does not accept resolutionPrices; use DIMENSION_TIERED instead";
        }
        if (resolved == PricingPlanEnum.TIERED) {
            String modeError = validateTierMode(config);
            return modeError != null ? modeError : validateTierComplete(config.tier());
        }
        if (resolved == PricingPlanEnum.PEAK_OFF_PEAK) {
            return validatePricing(config.pricing());
        }
        if (resolved == PricingPlanEnum.VENDOR_MINIMAX) {
            return validateVendorMiniMax(config);
        }
        if (resolved == PricingPlanEnum.DIMENSION_TIERED) {
            return validateDimensionTiered(config);
        }
        return null;
    }

    /** 视频扩展校验：合成量口径合法、输入秒不双计、分量单价与免费额度非负 */
    private static String validateVideo(VideoConfig video) {
        if (video == null) {
            return null;
        }
        String mode = BillingConfig.blankToNull(video.quantityMode());
        if (mode != null && !VideoConfig.SUPPORTED_QUANTITY_MODES.contains(mode.trim().toUpperCase())) {
            return "video.quantityMode must be OUTPUT, SUM or OUTPUT_PLUS_MAX";
        }
        if (video.inputVideoPerSecond() != null && video.inputVideoPerSecond().signum() < 0) {
            return "video.inputVideoPerSecond cannot be negative";
        }
        // 输入秒不得双计：SUM/OUTPUT_PLUS_MAX 已把输入秒折进计费量，再配独立分量 = 同一批秒收两次。
        // ⚠ 这条此前只挂在 DIMENSION_TIERED 上，FIXED/TIERED/PEAK_OFF_PEAK 能静默配出双计（资损且无报错）。
        if (video.inputVideoPerSecond() != null && mode != null) {
            String normalizedMode = mode.trim().toUpperCase();
            if (VideoConfig.QUANTITY_SUM.equals(normalizedMode)
                    || VideoConfig.QUANTITY_OUTPUT_PLUS_MAX.equals(normalizedMode)) {
                return "video.inputVideoPerSecond conflicts with video.quantityMode=" + normalizedMode
                        + " (input seconds would be billed twice)";
            }
        }
        if (video.inputImagePerImage() != null && video.inputImagePerImage().signum() < 0) {
            return "video.inputImagePerImage cannot be negative";
        }
        if (video.freeInputImages() != null && video.freeInputImages() < 0) {
            return "video.freeInputImages cannot be negative";
        }
        return null;
    }

    /** 附加费校验：金额必填非负、触发码合法 */
    private static String validateSurcharges(List<Surcharge> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        for (int index = 0; index < items.size(); index++) {
            Surcharge item = items.get(index);
            String path = "surcharges[" + index + "]";
            if (item == null) {
                return path + " must not be null";
            }
            if (item.amount() == null) {
                return path + ".amount is required";
            }
            if (item.amount().signum() < 0) {
                return path + ".amount cannot be negative";
            }
            String trigger = BillingConfig.blankToNull(item.trigger());
            if (trigger != null && !Surcharge.SUPPORTED_TRIGGERS.contains(trigger.trim().toUpperCase())) {
                return path + ".trigger must be ALWAYS, AUDIO or REFERENCE";
            }
        }
        return null;
    }

    /** TIERED 专属追加：tierMode 合法（缺省视为 PER_REQUEST） */
    private static String validateTierMode(BillingConfig config) {
        String mode = BillingConfig.blankToNull(config.tierMode());
        if (mode == null) {
            return null;
        }
        return SUPPORTED_TIER_MODES.contains(mode.toUpperCase())
                ? null
                : "tierMode must be PER_REQUEST or PROGRESSIVE";
    }

    /** VENDOR_MINIMAX 专属：必须带厂商定制分量形状（输入视频秒 / 输入图片张数 / 附加费之一），否则配置与类型自相矛盾 */
    private static String validateVendorMiniMax(BillingConfig config) {
        // MiniMax H3 的识别特征是「输入图片按张计费」：视频输入价可由输出档位价回落、免费额度默认 5、
        // 音频输入免费，故只有图片单价是必须显式配置的分量单价
        if (config.video() == null || config.video().inputImagePerImage() == null) {
            return "VENDOR_MINIMAX requires video.inputImagePerImage (per-image price)";
        }
        return null;
    }

    /**
     * DIMENSION_TIERED 专属：把「维度键拼错」从<b>静默回落</b>变成<b>拒绝保存</b>。
     *
     * <p>⭐ 本方案的取价算法与 {@link PricingPlanEnum#FIXED} 完全一致（都在
     * {@code FixedPricePolicy}），真正的增量就是这条校验。原因：维度键是<b>自由文本</b>，
     * 而取价是「逐级去尾找第一个命中的键」——键拼错时不会报错，而是悄悄退回
     * {@code defaultUnitPrice}，界面上看配置生效了、实际那档价从未命中过。</p>
     *
     * <p>四类拦截：</p>
     * <ol>
     *   <li><b>档位表不能空</b>：本方案存在的意义就是按档位取价，一张表没有等于退化成固定价。</li>
     *   <li><b>键形状必须合法</b>：{@code 720P:SOUND}（拼错维度名）、{@code 720P:AUDIO:REFERENCE}
     *       （顺序颠倒，永远进不了候选链）、{@code :AUDIO}（空档位名）一律拒绝。</li>
     *   <li><b>默认单价必填</b>：档位未命中时的兜底（厂商档位不可枚举，总有配不到的档）。
     *       缺它就是「识别不了的请求免费出账」，是资损。</li>
     *   <li><b>输入秒不得双计</b>（已上移到所有视频方案的基础校验 {@code validateVideo}）：
     *       {@code quantityMode=SUM/OUTPUT_PLUS_MAX} 已把输入秒折进计费量，再配
     *       {@code video.inputVideoPerSecond} 会同一批秒收两次；本方案不再重复校验。</li>
     * </ol>
     */
    private static String validateDimensionTiered(BillingConfig config) {
        if (config.resolutionPrices() == null || config.resolutionPrices().isEmpty()) {
            return "DIMENSION_TIERED requires resolutionPrices (at least one tier key)";
        }
        boolean tiered = false;
        for (String rawKey : config.resolutionPrices().keySet()) {
            String key = BillingConfig.blankToNull(rawKey);
            if (key == null) {
                return "DIMENSION_TIERED resolution key must not be blank";
            }
            // 输入侧分量键（INPUT_VIDEO: / INPUT_IMAGE:）同样是合法的分档键，剥掉前缀后按同一形状校验
            String name = key.trim();
            String prefix = "";
            if (upper(name).startsWith(VideoConfig.INPUT_VIDEO_PREFIX)) {
                prefix = VideoConfig.INPUT_VIDEO_PREFIX;
            } else if (upper(name).startsWith(VideoConfig.INPUT_IMAGE_PREFIX)) {
                prefix = VideoConfig.INPUT_IMAGE_PREFIX;
            }
            String body = name.substring(prefix.length());
            int colon = body.indexOf(':');
            if (BillingConfig.blankToNull(colon < 0 ? body : body.substring(0, colon)) == null) {
                return "DIMENSION_TIERED resolution key must start with a tier name: " + key;
            }
            String suffix = colon < 0 ? "" : body.substring(colon);
            if (!SUPPORTED_DIMENSION_SUFFIXES.contains(upper(suffix))) {
                return "DIMENSION_TIERED resolution key suffix must be empty, :AUDIO, :REFERENCE"
                        + " or :REFERENCE:AUDIO: " + key;
            }
            tiered = tiered || prefix.isEmpty();
        }
        if (!tiered) {
            return "DIMENSION_TIERED requires at least one tier price (without INPUT_VIDEO:/INPUT_IMAGE: prefix)";
        }
        if (config.defaultUnitPrice() == null) {
            return "DIMENSION_TIERED requires defaultUnitPrice (fallback when no dimension key matches)";
        }
        return null;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    /** TIERED 专属：tier 必须为非空数组，每项 condition 完整、unitPrices 完整保留全部 6 个价格字段 */
    private static String validateTierComplete(List<TierRule> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return "tier must be a non-empty array for TIERED plan";
        }
        for (TierRule rule : tiers) {
            if (rule == null || rule.condition() == null) {
                return "tier item.condition is required";
            }
            PriceSet prices = rule.unitPrices();
            if (prices == null) {
                return "tier item.unitPrices is required";
            }
            String missing = missingPriceField(prices);
            if (missing != null) {
                return "tier item.unitPrices must contain complete price fields, missing: " + missing;
            }
        }
        return null;
    }

    /** 返回 PriceSet 中第一个为 null 的字段名（TIERED 档位要求 6 个价格字段齐全），全齐返回 null */
    private static String missingPriceField(PriceSet prices) {
        if (prices.input() == null) {
            return "input";
        }
        if (prices.output() == null) {
            return "output";
        }
        if (prices.cacheRead() == null) {
            return "cacheRead";
        }
        if (prices.cacheWrite() == null) {
            return "cacheWrite";
        }
        if (prices.cacheWrite5m() == null) {
            return "cacheWrite5m";
        }
        if (prices.cacheWrite1h() == null) {
            return "cacheWrite1h";
        }
        return null;
    }

    private static String validateTier(List<TierRule> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        BigDecimal previousFrom = null;
        BigDecimal previousTo = null;
        for (TierRule rule : tiers) {
            if (rule == null || rule.condition() == null) {
                return "tier item.condition is required";
            }
            BigDecimal from = rule.condition().from();
            BigDecimal to = rule.condition().to();
            if (from == null) {
                return "tier item.condition.from is required";
            }
            if (from.signum() < 0) {
                return "tier item.condition.from cannot be negative";
            }
            if (to != null && from.compareTo(to) > 0) {
                return "tier item.condition.from cannot be greater than to";
            }
            if (previousFrom != null) {
                if (previousTo == null) {
                    return "tier intervals overlap: an open-ended interval must be the last one";
                }
                if (from.compareTo(previousTo) <= 0) {
                    return "tier intervals overlap";
                }
            }
            previousFrom = from;
            previousTo = to;
            if (rule.unitPrices() != null) {
                for (PriceKind kind : PriceKind.values()) {
                    BigDecimal price = rule.unitPrices().valueOf(kind);
                    if (price != null && price.signum() < 0) {
                        return "tier price cannot be negative: " + BillingConfig.jsonName(kind);
                    }
                }
            }
        }
        return null;
    }

    // ==================== 高低峰校验 ====================

    /**
     * PEAK_OFF_PEAK 专属：pricing 必填，且 rateMode / timezone / 相位 / 倍率 / 星期 / 时段全部合法。
     *
     * <p>与时区、幅值相关的白名单收在这里：{@code timezone} 使用 UTC 偏移（例如 {@code UTC+8}），
     * {@code holidayPolicy} 首期只允许 {@code OFF_PEAK}，倍率必须落在 {@code (0, 1]}。
     */
    private static String validatePricing(Pricing pricing) {
        if (pricing == null) {
            return "pricing is required for PEAK_OFF_PEAK plan";
        }
        String rateMode = BillingConfig.blankToNull(pricing.rateMode());
        if (rateMode == null) {
            rateMode = BillingConfig.RATE_MODE_MULTIPLIER;
        }
        if (!BillingConfig.RATE_MODE_MULTIPLIER.equals(rateMode) && !BillingConfig.RATE_MODE_ABSOLUTE.equals(rateMode)) {
            return "pricing.rateMode must be MULTIPLIER or ABSOLUTE";
        }
        String timezone = BillingConfig.blankToNull(pricing.timezone());
        if (timezone == null || !BillingConfig.SUPPORTED_TIMEZONES.contains(timezone)) {
            return "pricing.timezone must be a valid UTC offset";
        }
        List<PhaseRule> phases = pricing.phases();
        if (phases == null || phases.isEmpty()) {
            return "pricing.phases must be a non-empty array";
        }
        PhaseRule first = phases.get(0);
        if (first == null || !PHASE_PEAK.equals(BillingConfig.blankToNull(first.phase()))) {
            return "pricing.phases first item must be PEAK";
        }
        PhaseRule last = phases.get(phases.size() - 1);
        if (last == null || last.condition() == null
                || !last.condition().isDefault() || !PHASE_OFF_PEAK.equals(last.phase())) {
            return "pricing.phases last item must be the DEFAULT off-peak fallback";
        }
        String makeUpPolicy = null;
        boolean absolute = BillingConfig.RATE_MODE_ABSOLUTE.equals(rateMode);
        for (int index = 0; index < phases.size(); index++) {
            PhaseRule rule = phases.get(index);
            String item = "pricing.phases[" + index + "]";
            if (rule == null || rule.condition() == null) {
                return item + ".condition is required";
            }
            if (absolute) {
                String unitPriceError = validateAbsoluteUnitPrices(rule.unitPrices(), item);
                if (unitPriceError != null) {
                    return unitPriceError;
                }
            } else {
                String multiplierError = validateMultiplier(rule.multiplier(), item);
                if (multiplierError != null) {
                    return multiplierError;
                }
            }
            if (!SUPPORTED_PHASES.contains(BillingConfig.blankToNull(rule.phase()))) {
                return item + ".phase must be PEAK or OFF_PEAK";
            }
            String conditionError = validatePhaseCondition(rule.condition(), item);
            if (conditionError != null) {
                return conditionError;
            }
            // 调休日是否按工作日窗口判定由判定器读「第一条高峰规则」的策略决定，各条必须一致
            if (!rule.condition().isDefault() && index < phases.size() - 1) {
                String current = normalizedMakeUpPolicy(rule.condition());
                if (makeUpPolicy == null) {
                    makeUpPolicy = current;
                } else if (!makeUpPolicy.equals(current)) {
                    return item + ".condition.makeUpWorkdayPolicy must be identical across all peak rules";
                }
            }
        }
        return null;
    }

    /** ABSOLUTE 模式：每个相位必须自带 unitPrices，且已配置价格非负（分项完整性由前端按模式校验） */
    private static String validateAbsoluteUnitPrices(PriceSet unitPrices, String item) {
        if (unitPrices == null) {
            return item + ".unitPrices is required for ABSOLUTE rate mode";
        }
        for (PriceKind kind : PriceKind.values()) {
            BigDecimal price = unitPrices.valueOf(kind);
            if (price != null && price.signum() < 0) {
                return item + ".unitPrices." + BillingConfig.jsonName(kind) + " cannot be negative";
            }
        }
        return null;
    }

    /** 调休策略归一：缺失按默认 OFF_PEAK */
    private static String normalizedMakeUpPolicy(PhaseCondition condition) {
        String policy = BillingConfig.blankToNull(condition.makeUpWorkdayPolicy());
        return policy == null ? "OFF_PEAK" : policy;
    }

    /** 倍率必须落在 {@code (0, 1]}：低峰是打折，不允许免费也不允许涨价 */
    private static String validateMultiplier(BigDecimal multiplier, String item) {
        if (multiplier == null) {
            return item + ".multiplier is required";
        }
        if (multiplier.signum() <= 0) {
            return item + ".multiplier must be greater than 0";
        }
        if (multiplier.compareTo(BigDecimal.ONE) > 0) {
            return item + ".multiplier cannot be greater than 1";
        }
        return null;
    }

    /** 相位条件校验：兜底项要求 type=DEFAULT 且不带星期/时段；其余项要求星期与时段齐全、时段合法不重叠 */
    private static String validatePhaseCondition(PhaseCondition condition, String item) {
        String holidayPolicy = condition.holidayPolicy();
        if (holidayPolicy != null && !SUPPORTED_HOLIDAY_POLICIES.contains(holidayPolicy)) {
            return item + ".condition.holidayPolicy must be OFF_PEAK";
        }
        String makeUpPolicy = condition.makeUpWorkdayPolicy();
        if (makeUpPolicy != null && !SUPPORTED_MAKE_UP_POLICIES.contains(makeUpPolicy)) {
            return item + ".condition.makeUpWorkdayPolicy must be OFF_PEAK or FOLLOW_WEEKDAY_WINDOWS";
        }
        if (condition.isDefault()) {
            return null;
        }
        if (condition.weekdays() == null || condition.weekdays().isEmpty()) {
            return item + ".condition.weekdays must not be empty";
        }
        for (Integer day : condition.weekdays()) {
            if (day == null || day < 1 || day > 7) {
                return item + ".condition.weekdays must contain ISO day numbers 1 (Monday) to 7 (Sunday)";
            }
        }
        List<Window> windows = condition.windows();
        if (windows == null || windows.isEmpty()) {
            return item + ".condition.windows must not be empty";
        }
        int previousEndMinute = -1;
        for (int index = 0; index < windows.size(); index++) {
            Window window = windows.get(index);
            String windowItem = item + ".condition.windows[" + index + "]";
            // 唯一换算出口：end 可为 24:00（数轴 1440），格式非法/跨午夜/重叠全在数轴上判
            WindowRange range = BillingConfig.windowRangeOf(window);
            if (range == null) {
                return windowItem + " must use HH:mm clock format (end may also be 24:00)";
            }
            // 禁止跨午夜：22:00-02:00 须拆成两段；00:00-00:00 不代表全天
            if (range.startMinute() >= range.endMinute()) {
                return windowItem + ".start must be before end and cannot cross midnight";
            }
            if (previousEndMinute >= 0 && range.startMinute() < previousEndMinute) {
                return windowItem + " overlaps the previous window";
            }
            previousEndMinute = range.endMinute();
        }
        return null;
    }
}
