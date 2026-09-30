package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.pricing.HolidayCalendar;
import org.chobit.knot.gateway.pricing.PeakOffPeakResolver;
import org.chobit.knot.gateway.pricing.PhaseDecision;
import org.chobit.knot.gateway.util.JsonKit;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 计费配置（kb_billing_rule_versions.config_json）的描述类。
 *
 * <p>两层职责分离：计费模式（billingMode，决定“量”）+ 进阶定价方案（pricingPlan，决定“价”）。
 * 方案类型由版本结构化列 pricing_plan 表达，config_json 不重复保存方案类型；
 * 阶梯明细为根级 tier（不增加 pricing 包装层），高低峰（阶段三）才使用根级 pricing 对象。
 *
 * <p>约定结构（价格字段全部具名，不用 Map 承载已知价格）：
 * <pre>
 * {
 *   "defaultUnitPrice": 0.002,     // 可选，简单模式/回退兜底单价
 *   "basePrices": {                // 可选，TOKEN 模式分项单价（与 TierRule.unitPrices 同构）
 *     "input": 4, "output": 20, "cacheRead": 0.2, "cacheWrite": 5,
 *     "cacheWrite5m": 5, "cacheWrite1h": 5
 *   },
 *   "tier": [                      // pricingPlan=TIERED 时的阶梯档位（from 含、to 含，to 省略表示上不封顶）
 *     { "condition": { "from": 0, "to": 1000000 }, "unitPrices": { "input": 4, "output": 20 } },
 *     { "condition": { "from": 1000001 },            "unitPrices": { "input": 2, "output": 10 } }
 *   ],
 *   "pricing": {                   // pricingPlan=PEAK_OFF_PEAK 时的高低峰倍率（不含 type，方案类型由版本列表达）
 *     "rateMode": "MULTIPLIER", "timezone": "UTC",
 *     "phases": [
 *       { "condition": { "weekdays": ["MONDAY"], "windows": [{"start":"01:00","end":"04:00"}],
 *                        "holidayPolicy": "OFF_PEAK", "makeUpWorkdayPolicy": "OFF_PEAK" },
 *         "phase": "PEAK", "multiplier": 1 },
 *       { "condition": { "type": "DEFAULT" }, "phase": "OFF_PEAK", "multiplier": 0.8 }
 *     ]
 *   }
 * }
 * </pre>
 *
 * <p>高低峰只负责在高低峰相位上给出倍率，价格本体始终由模式层产出（{@link PricingPlan} 的两条解析路径），
 * 两者正交：没有 occurredAt 时不放大不打折（倍率固定 1），保证无时间来源的调用点行为不变。
 *
 * <p>价格解析链：命中阶梯档位的 unitPrices[kind] -&gt; basePrices[kind] -&gt; defaultUnitPrice -&gt; fallback。
 * 阶梯只服务 TOKEN 类模式（首期档位基准=本次请求总 Token，整笔命中一个档位，不拆段）；
 * 简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING）固定价，只取 defaultUnitPrice。
 * 全程 BigDecimal，不做 double 运算；网关计算器与管理端计费详情两条路径共用。
 * 未知 JSON 字段忽略（CUSTOM 模式透传内容由 DB 原样保存，服务端不重写）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BillingConfig(
        BigDecimal defaultUnitPrice,
        PriceSet basePrices,
        List<TierRule> tier,
        Map<String, BigDecimal> resolutionPrices,
        Pricing pricing) {

    /** 首期唯一的计价方式：在模式层基础单价上乘相位倍率 */
    private static final String RATE_MODE_MULTIPLIER = "MULTIPLIER";

    /** 首期时区白名单：只接受 UTC，禁止 {@code +08:00} 这类固定偏移（公共导出，判定器须同源） */
    public static final Set<String> SUPPORTED_TIMEZONES = Set.of("UTC");

    private static final String PHASE_PEAK = "PEAK";
    private static final String PHASE_OFF_PEAK = "OFF_PEAK";
    private static final Set<String> SUPPORTED_PHASES = Set.of(PHASE_PEAK, PHASE_OFF_PEAK);

    /** 节假日策略首期固定：节假日整日低峰，覆盖星期与时段 */
    private static final Set<String> SUPPORTED_HOLIDAY_POLICIES = Set.of("OFF_PEAK");

    /** 调休策略：整日低峰，或按所调休星期的高峰窗口判定 */
    private static final Set<String> SUPPORTED_MAKE_UP_POLICIES = Set.of("OFF_PEAK", "FOLLOW_WEEKDAY_WINDOWS");

    /** 时段时钟格式（线程安全，替代 SimpleDateFormat） */
    private static final DateTimeFormatter CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 价格种类：对应 config_json 中的具名价格字段 */
    public enum PriceKind {
        INPUT, OUTPUT, CACHE_READ, CACHE_WRITE
    }

    /** 价格种类 -> JSON 键名（校验错误提示用） */
    public static String jsonName(PriceKind kind) {
        return switch (kind) {
            case INPUT -> "input";
            case OUTPUT -> "output";
            case CACHE_READ -> "cacheRead";
            case CACHE_WRITE -> "cacheWrite";
        };
    }

    /**
     * 一组具名价格（basePrices 与 tier[].unitPrices 同构）。
     * 字段名与 JSON 键一一对应：input / output / cacheRead / cacheWrite / cacheWrite5m / cacheWrite1h。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceSet(
            BigDecimal input,
            BigDecimal output,
            BigDecimal cacheRead,
            BigDecimal cacheWrite,
            BigDecimal cacheWrite5m,
            BigDecimal cacheWrite1h) {

        /** 按价格种类取值；该种类未配置返回 null */
        public BigDecimal valueOf(PriceKind kind) {
            return switch (kind) {
                case INPUT -> input;
                case OUTPUT -> output;
                case CACHE_READ -> cacheRead;
                case CACHE_WRITE -> cacheWrite;
            };
        }
    }

    /** 单个阶梯档位：条件区间 + 该档完整价格 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TierRule(Condition condition, PriceSet unitPrices) {
    }

    /** 阶梯区间：from 必填（含），to 可空（含，空表示上不封顶） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(BigDecimal from, BigDecimal to) {
    }

    // ==================== 高低峰定价（pricingPlan=PEAK_OFF_PEAK） ====================

    /**
     * 高低峰配置：只描述“价”怎么随时间变化，不含任何模式专属字段。
     *
     * <p>方案类型不写进 JSON（由版本列 {@code pricing_plan} 表达），故此结构无 {@code type} 字段，
     * 避免与版本列双写产生不一致。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(String rateMode, String timezone, List<PhaseRule> phases) {
    }

    /** 相位规则：命中条件 -> 所属相位 + 该相位倍率 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhaseRule(PhaseCondition condition, String phase, BigDecimal multiplier) {
    }

    /**
     * 相位命中条件。
     *
     * <p>{@code type} 为 {@code DEFAULT} 时表示兜底低峰，不参与星期/时段判定；
     * 其余字段语义见 {@link PeakOffPeakResolver}。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhaseCondition(List<DayOfWeek> weekdays,
                                 List<Window> windows,
                                 String holidayPolicy,
                                 String makeUpWorkdayPolicy,
                                 String type) {

        /** 是否为兜底低峰项（末项） */
        public boolean isDefault() {
            return "DEFAULT".equalsIgnoreCase(type);
        }
    }

    /** 日内时段 {@code HH:mm}：start 含、end 不含；不允许跨午夜（start &lt; end） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Window(String start, String end) {
    }

    /**
     * 价格解析上下文：用量（阶梯用）+ 发生时间（高低峰用）。
     *
     * <p>时间缺失（{@code occurredAt == null}）时高低峰退化为“不调整”，
     * 使不支持时间来源的调用点（如管理端离线试算）结果保持稳定。
     */
    public record PricingContext(long usageAmount, Instant occurredAt) {

        /** 无时间来源的上下文（阶梯判定可用，高低峰不生效） */
        public static PricingContext ofAmount(long usageAmount) {
            return new PricingContext(usageAmount, null);
        }
    }

    // ==================== 解析 ====================

    /**
     * 严格解析：json 为空返回 null；非法 JSON 抛 {@link IllegalArgumentException}（供保存前校验用）。
     */
    public static BillingConfig parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MapperHolder.MAPPER.readValue(json, BillingConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("config_json must be a valid JSON object", e);
        }
    }

    /**
     * 宽松解析：空串或非法 JSON 均返回 null（供计费热路径用，解析失败按无配置计费）。
     */
    public static BillingConfig fromJsonOrNull(String json) {
        try {
            return parse(json);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 延迟持有 ObjectMapper，避免 record 泛型静态初始化顺序问题 */
    private static final class MapperHolder {
        static final ObjectMapper MAPPER = JsonKit.mapper();
    }

    /** 视频分辨率单价：resolutionPrices[resolution]，未配置返回 null */
    public BigDecimal resolutionPrice(String resolution) {
        return resolutionPrices == null ? null : resolutionPrices.get(resolution);
    }

    // ==================== 进阶方案领域层 ====================

    /**
     * 进阶定价方案的领域对象：封装条件命中与价格解析。
     * 由 {@link #pricingPlan(PricingPlanEnum)} 依据版本列 pricing_plan 构建，
     * 计算器只接收本接口，不直接读取 JSON 字符串、不做字符串键分支。
     */
    public interface PricingPlan {

        PricingPlanEnum plan();

        /** TOKEN 类模式单价：阶梯命中 -&gt; basePrices[kind] -&gt; defaultUnitPrice -&gt; fallback，再乘相位倍率 */
        BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback);

        /** 简单模式/兜底单价：defaultUnitPrice -&gt; fallback，再乘相位倍率 */
        BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback);

        /**
         * 旧签名（无时间上下文）默认委托到带上下文的重载：occurredAt 为 null，高低峰不做调整。
         * 存量调用点不改也能编译，但网关热路径应改传带 occurredAt 的 PricingContext，否则高低峰不生效。
         */
        default BigDecimal resolvePrice(PriceKind kind, long usageAmount, BigDecimal fallback) {
            return resolvePrice(kind, PricingContext.ofAmount(usageAmount), fallback);
        }

        /** 旧签名（无时间上下文）默认委托，语义同 {@link #resolvePrice(PriceKind, long, BigDecimal)} */
        default BigDecimal resolveDefaultPrice(BigDecimal fallback) {
            return resolveDefaultPrice(new PricingContext(0L, null), fallback);
        }
    }

    /** 固定价：基础价格直出 */
    private record FixedPricing(PriceSet basePrices, BigDecimal defaultUnitPrice) implements PricingPlan {

        @Override
        public PricingPlanEnum plan() {
            return PricingPlanEnum.FIXED;
        }

        @Override
        public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
            BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
            if (fromBase != null) {
                return fromBase;
            }
            return defaultUnitPrice != null ? defaultUnitPrice : fallback;
        }

        @Override
        public BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback) {
            return defaultUnitPrice != null ? defaultUnitPrice : fallback;
        }
    }

    /** 阶梯价：按用量命中档位单价，未命中回退基础价格 */
    private record TieredPricing(PriceSet basePrices, BigDecimal defaultUnitPrice, List<TierRule> tier)
            implements PricingPlan {

        @Override
        public PricingPlanEnum plan() {
            return PricingPlanEnum.TIERED;
        }

        @Override
        public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
            BigDecimal fromTier = resolveFromTier(kind, context == null ? 0L : context.usageAmount());
            if (fromTier != null) {
                return fromTier;
            }
            BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
            if (fromBase != null) {
                return fromBase;
            }
            return defaultUnitPrice != null ? defaultUnitPrice : fallback;
        }

        @Override
        public BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback) {
            return defaultUnitPrice != null ? defaultUnitPrice : fallback;
        }

        private BigDecimal resolveFromTier(PriceKind kind, long usageAmount) {
            if (usageAmount <= 0 || tier == null) {
                return null;
            }
            BigDecimal amount = BigDecimal.valueOf(usageAmount);
            for (TierRule rule : tier) {
                if (rule == null || rule.condition() == null || rule.unitPrices() == null) {
                    continue;
                }
                BigDecimal from = rule.condition().from();
                BigDecimal to = rule.condition().to();
                if (from == null || from.compareTo(amount) > 0) {
                    continue;
                }
                if (to != null && to.compareTo(amount) < 0) {
                    continue;
                }
                BigDecimal matched = rule.unitPrices().valueOf(kind);
                if (matched != null) {
                    return matched;
                }
            }
            return null;
        }
    }

    /**
     * 高低峰价：在模式层产出的基础单价上乘相位倍率，自身不持有价格。
     *
     * <p>价格本体仍完全由 {@link FixedPricing} 的解析链给出（basePrices -&gt; defaultUnitPrice -&gt; fallback），
     * 这里只按 {@link PeakOffPeakResolver} 判定的相位乘系数，因此对所有计费模式是同一套代码；
     * 时间缺失时倍率为 1，结果退化为普通固定价。
     */
    private record PeakOffPeakPricing(PriceSet basePrices,
                                      BigDecimal defaultUnitPrice,
                                      Pricing pricing,
                                      HolidayCalendar calendar) implements PricingPlan {

        @Override
        public PricingPlanEnum plan() {
            return PricingPlanEnum.PEAK_OFF_PEAK;
        }

        @Override
        public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
            BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
            BigDecimal base = fromBase != null ? fromBase : (defaultUnitPrice != null ? defaultUnitPrice : fallback);
            return applyMultiplier(base, context);
        }

        @Override
        public BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback) {
            BigDecimal base = defaultUnitPrice != null ? defaultUnitPrice : fallback;
            return applyMultiplier(base, context);
        }

        private BigDecimal applyMultiplier(BigDecimal basePrice, PricingContext context) {
            if (basePrice == null) {
                return null;
            }
            return PeakOffPeakResolver.apply(basePrice, pricing, context, calendar);
        }
    }

    /**
     * 依据版本列 pricing_plan 构建方案领域对象；配置解析失败视为无配置（返回 null 由调用方兜底）。
     */
    public PricingPlan pricingPlan(PricingPlanEnum plan) {
        return pricingPlan(plan, HolidayCalendar.EMPTY);
    }

    /**
     * 带日历的构建入口：网关与管理端两条路径共用，只有日历来源不同。
     */
    public PricingPlan pricingPlan(PricingPlanEnum plan, HolidayCalendar calendar) {
        PricingPlanEnum resolved = plan == null ? PricingPlanEnum.FIXED : plan;
        HolidayCalendar effective = calendar == null ? HolidayCalendar.EMPTY : calendar;
        return switch (resolved) {
            case FIXED -> new FixedPricing(basePrices, defaultUnitPrice);
            case TIERED -> new TieredPricing(basePrices, defaultUnitPrice, tier);
            case PEAK_OFF_PEAK -> new PeakOffPeakPricing(basePrices, defaultUnitPrice, pricing, effective);
        };
    }

    // ==================== 校验 ====================

    /**
     * 基础结构校验：返回 null 表示合法，否则返回错误描述。
     * 覆盖：价格非负（defaultUnitPrice / basePrices / resolutionPrices）、
     * tier 的 from 必填且非负、from&lt;=to、档位不重叠（开放档位必须是最后一个）。
     */
    public String validate() {
        if (defaultUnitPrice != null && defaultUnitPrice.signum() < 0) {
            return "defaultUnitPrice cannot be negative";
        }
        if (basePrices != null) {
            for (PriceKind kind : PriceKind.values()) {
                BigDecimal price = basePrices.valueOf(kind);
                if (price != null && price.signum() < 0) {
                    return "base price cannot be negative: " + jsonName(kind);
                }
            }
        }
        if (resolutionPrices != null) {
            for (Map.Entry<String, BigDecimal> entry : resolutionPrices.entrySet()) {
                if (entry.getValue() == null || entry.getValue().signum() < 0) {
                    return "resolution price cannot be negative: " + entry.getKey();
                }
            }
        }
        return validateTier(tier);
    }

    /**
     * 方案级校验：按 pricingPlan 追加约束。
     * TIERED 要求 tier 非空且每项 unitPrices 完整保留全部价格字段（价格非负）；
     * PEAK_OFF_PEAK 属阶段三，暂不接受保存。
     */
    public String validate(PricingPlanEnum plan) {
        PricingPlanEnum resolved = plan == null ? PricingPlanEnum.FIXED : plan;
        if (!resolved.isAvailable()) {
            return "pricing plan not supported yet: " + resolved.code();
        }
        String baseError = validate();
        if (baseError != null) {
            return baseError;
        }
        if (resolved == PricingPlanEnum.TIERED) {
            return validateTierComplete(tier);
        }
        if (resolved == PricingPlanEnum.PEAK_OFF_PEAK) {
            return validatePricing(pricing);
        }
        return null;
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
                        return "tier price cannot be negative: " + jsonName(kind);
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
     * <p>与时区、幅值相关的白名单收在这里：{@code timezone} 首期只允许 {@code UTC}（禁止 {@code +08:00} 这类固定偏移），
     * {@code holidayPolicy} 首期只允许 {@code OFF_PEAK}，倍率必须落在 {@code (0, 1]}。
     */
    private static String validatePricing(Pricing pricing) {
        if (pricing == null) {
            return "pricing is required for PEAK_OFF_PEAK plan";
        }
        if (!RATE_MODE_MULTIPLIER.equals(blankToNull(pricing.rateMode()))) {
            return "pricing.rateMode must be MULTIPLIER";
        }
        String timezone = blankToNull(pricing.timezone());
        if (timezone == null || !SUPPORTED_TIMEZONES.contains(timezone)) {
            return "pricing.timezone must be one of " + SUPPORTED_TIMEZONES;
        }
        List<PhaseRule> phases = pricing.phases();
        if (phases == null || phases.isEmpty()) {
            return "pricing.phases must be a non-empty array";
        }
        PhaseRule first = phases.get(0);
        if (first == null || !PHASE_PEAK.equals(blankToNull(first.phase()))) {
            return "pricing.phases first item must be PEAK";
        }
        PhaseRule last = phases.get(phases.size() - 1);
        if (last == null || last.condition() == null
                || !last.condition().isDefault() || !PHASE_OFF_PEAK.equals(last.phase())) {
            return "pricing.phases last item must be the DEFAULT off-peak fallback";
        }
        for (int index = 0; index < phases.size(); index++) {
            PhaseRule rule = phases.get(index);
            String item = "pricing.phases[" + index + "]";
            if (rule == null || rule.condition() == null) {
                return item + ".condition is required";
            }
            String multiplierError = validateMultiplier(rule.multiplier(), item);
            if (multiplierError != null) {
                return multiplierError;
            }
            if (!SUPPORTED_PHASES.contains(blankToNull(rule.phase()))) {
                return item + ".phase must be PEAK or OFF_PEAK";
            }
            String conditionError = validatePhaseCondition(rule.condition(), item);
            if (conditionError != null) {
                return conditionError;
            }
        }
        return null;
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
        List<Window> windows = condition.windows();
        if (windows == null || windows.isEmpty()) {
            return item + ".condition.windows must not be empty";
        }
        LocalTime previousEnd = null;
        for (int index = 0; index < windows.size(); index++) {
            Window window = windows.get(index);
            String windowItem = item + ".condition.windows[" + index + "]";
            if (window == null) {
                return windowItem + " is required";
            }
            LocalTime start = parseClock(window.start());
            LocalTime end = parseClock(window.end());
            if (start == null || end == null) {
                return windowItem + " must use HH:mm clock format";
            }
            // 禁止跨午夜：22:00-02:00 须拆成两段；00:00-00:00 不代表全天
            if (!start.isBefore(end)) {
                return windowItem + ".start must be before end and cannot cross midnight";
            }
            if (previousEnd != null && start.isBefore(previousEnd)) {
                return windowItem + " overlaps the previous window";
            }
            previousEnd = end;
        }
        return null;
    }

    /** {@code HH:mm} 解析；非法格式返回 null（{@link DateTimeFormatter} 线程安全，不做静态可变缓存） */
    private static LocalTime parseClock(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(text, CLOCK_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
