package org.chobit.knot.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.pricing.HolidayCalendar;
import org.chobit.knot.common.pricing.PeakOffPeakResolver;
import org.chobit.knot.common.util.JsonKit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 计费配置，落 {@code kb_billing_rule_versions.config_json}；方案类型由版本列 {@code pricing_plan} 表达，JSON 内不重复保存。
 *
 * 两层：{@code billingMode} 决定「量」，{@code pricingPlan} 决定「价」；阶梯明细为根级 {@code tier}，高低峰为根级 {@code pricing}。
 * 价格解析链：阶梯 {@code unitPrices[kind]} -&gt; {@code basePrices[kind]} -&gt; {@code defaultUnitPrice} -&gt; fallback，全程 BigDecimal。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BillingConfig(
        BigDecimal defaultUnitPrice,
        PriceSet basePrices,
        List<TierRule> tier,
        /**
         * 阶梯累计方式（仅 pricingPlan=TIERED 时生效）：{@code PER_REQUEST}（默认）整笔用量落一档、
         * 全量按该档单价；{@code PROGRESSIVE} 逐单位判定所属档位、单价逐单位累加。
         */
        String tierMode,
        Map<String, BigDecimal> resolutionPrices,
        /** 视频模式扩展：合成量口径 + 多分量单价 + 免费额度；null 时退化为「仅按输出秒数」 */
        VideoConfig video,
        /** 附加费：与用量无关的常数项，命中触发条件即加进总价 */
        List<Surcharge> surcharges,
        Pricing pricing,
        /** 缓存写方式（TOKEN 模式）：standard=单字段缓存写，ttl=5m/1h 分字段；null 视为 standard */
        String cacheWriteMode) {

    /**
     * tierMode 取值：{@code PER_REQUEST} 整笔落一档 / {@code PROGRESSIVE} 逐单位累加。
     */
    public static final String TIER_MODE_PER_REQUEST = "PER_REQUEST";
    public static final String TIER_MODE_PROGRESSIVE = "PROGRESSIVE";

    /** 首期唯一的计价方式：在模式层基础单价上乘相位倍率 */
    static final String RATE_MODE_MULTIPLIER = "MULTIPLIER";

    /** 独立单价模式：命中相位自身携带 unitPrices，不再对模式层基础价做乘法 */
    static final String RATE_MODE_ABSOLUTE = "ABSOLUTE";

    /** UTC 偏移白名单，覆盖 UTC-12 至 UTC+14。 */
    public static final Set<String> SUPPORTED_TIMEZONES = java.util.stream.IntStream.rangeClosed(-12, 14)
            .mapToObj(offset -> offset < 0 ? "UTC" + offset : "UTC+" + offset)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());


    /** 价格种类：对应 config_json 中的具名价格字段 */
    public enum PriceKind {
        INPUT, OUTPUT, CACHE_READ, CACHE_WRITE, CACHE_WRITE_5M, CACHE_WRITE_1H
    }

    /** 价格种类 -> JSON 键名（校验错误提示用） */
    public static String jsonName(PriceKind kind) {
        return switch (kind) {
            case INPUT -> "input";
            case OUTPUT -> "output";
            case CACHE_READ -> "cacheRead";
            case CACHE_WRITE -> "cacheWrite";
            case CACHE_WRITE_5M -> "cacheWrite5m";
            case CACHE_WRITE_1H -> "cacheWrite1h";
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
                case CACHE_WRITE_5M -> cacheWrite5m;
                case CACHE_WRITE_1H -> cacheWrite1h;
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

    // ==================== 视频模式扩展（2026-10-25） ====================

    /**
     * 视频模式扩展配置：合成量口径 + 输入侧分量单价 + 输入图片免费额度。
     *
     * {@code quantityMode} 三种口径：{@code OUTPUT}（默认，输出视频秒数）、{@code SUM}（输入秒 + 输出秒）、
     * {@code OUTPUT_PLUS_MAX}（输出秒 + max(输入秒, 输出秒)，输入为 0 时即输出秒）。
     * 输入侧分量 {@code inputVideoPerSecond} / {@code inputImagePerImage} 非空时才启用，未配置时回落 {@code defaultUnitPrice}。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoConfig(String quantityMode,
                              BigDecimal inputVideoPerSecond,
                              BigDecimal inputImagePerImage,
                              Integer freeInputImages) {

        public static final String QUANTITY_OUTPUT = "OUTPUT";
        public static final String QUANTITY_SUM = "SUM";
        public static final String QUANTITY_OUTPUT_PLUS_MAX = "OUTPUT_PLUS_MAX";
        public static final Set<String> SUPPORTED_QUANTITY_MODES =
                Set.of(QUANTITY_OUTPUT, QUANTITY_SUM, QUANTITY_OUTPUT_PLUS_MAX);

        /** 输入视频分量的维度键前缀 */
        public static final String INPUT_VIDEO_PREFIX = "INPUT_VIDEO:";
        /** 输入图片分量的维度键前缀 */
        public static final String INPUT_IMAGE_PREFIX = "INPUT_IMAGE:";

        /**
         * 合成量：把输入秒按口径折进输出秒，产出单一计费量。
         *
         * {@code OUTPUT_PLUS_MAX} 下：无输入即输出秒数，输入 ≤ 输出为输出×2，输入 &gt; 输出为输入+输出。
         */
        public long quantity(long outputSeconds, long inputSeconds) {
            String mode = blankToNull(quantityMode) == null ? QUANTITY_OUTPUT : quantityMode.trim().toUpperCase();
            if (outputSeconds <= 0) {
                return Math.max(outputSeconds, 0L);
            }
            return switch (mode) {
                case QUANTITY_SUM -> outputSeconds + Math.max(inputSeconds, 0L);
                case QUANTITY_OUTPUT_PLUS_MAX -> inputSeconds > 0
                        ? outputSeconds + Math.max(inputSeconds, outputSeconds)
                        : outputSeconds;
                default -> outputSeconds;
            };
        }

        /** 输入图片计费张数：扣掉免费额度后剩余；免费额度未配置时为全部 */
        public long billableImageCount(long imageCount) {
            long free = freeInputImages == null || freeInputImages < 0 ? 0L : freeInputImages;
            return Math.max(imageCount - free, 0L);
        }

        /** 是否启用输入视频分量 */
        public boolean billsInputVideo() {
            return inputVideoPerSecond != null;
        }

        /** 是否启用输入图片分量 */
        public boolean billsInputImage() {
            return inputImagePerImage != null;
        }
    }

    /**
     * 附加费：与用量无关的常数项。
     *
     * {@code trigger} 决定何时收取：{@code ALWAYS}（恒收）/ {@code AUDIO}（请求侧要了声音）/ {@code REFERENCE}（请求侧带了参考输入）；
     * 未识别的触发码按 {@code ALWAYS}。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Surcharge(String code, String trigger, BigDecimal amount) {

        public static final String TRIGGER_ALWAYS = "ALWAYS";
        public static final String TRIGGER_AUDIO = "AUDIO";
        public static final String TRIGGER_REFERENCE = "REFERENCE";
        public static final Set<String> SUPPORTED_TRIGGERS =
                Set.of(TRIGGER_ALWAYS, TRIGGER_AUDIO, TRIGGER_REFERENCE);

        /** 该附加费是否在本次请求上触发 */
        public boolean triggered(boolean sound, boolean reference) {
            String mode = blankToNull(trigger) == null ? TRIGGER_ALWAYS : trigger.trim().toUpperCase();
            return switch (mode) {
                case TRIGGER_AUDIO -> sound;
                case TRIGGER_REFERENCE -> reference;
                default -> true;
            };
        }
    }

    // ==================== 高低峰定价（pricingPlan=PEAK_OFF_PEAK） ====================

    /**
     * 高低峰配置：只描述「价」怎么随时间变化，不含模式专属字段；无 {@code type} 字段，方案类型由版本列 {@code pricing_plan} 表达。
     *
     * {@code rateMode}：{@code MULTIPLIER}（默认，最终单价 = 模式层基础价 × 命中相位倍率）/
     * {@code ABSOLUTE}（最终单价 = 相位自带的 {@code unitPrices}）；ABSOLUTE 下每个 {@link PhaseRule} 必须自带 {@code unitPrices}，
     * 简单模式的单单价收在 {@code unitPrices.input} 槽位。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(String rateMode, String timezone, List<PhaseRule> phases) {
    }

    /** 相位规则：命中条件 -> 所属相位 + 该相位倍率；ABSOLUTE 模式下额外携带自身单价 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhaseRule(PhaseCondition condition, String phase, BigDecimal multiplier, PriceSet unitPrices) {
    }

    /**
     * 相位命中条件。{@code type} 为 {@code DEFAULT} 时表示兜底低峰，不参与星期 / 时段判定；
     * {@code weekdays} 为 ISO DayOfWeek 数字（周一=1 ... 周日=7），其余字段语义见 {@link PeakOffPeakResolver}。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhaseCondition(List<Integer> weekdays,
                                 List<Window> windows,
                                 String holidayPolicy,
                                 String makeUpWorkdayPolicy,
                                 String type) {

        /** 是否为兜底低峰项（末项） */
        public boolean isDefault() {
            return "DEFAULT".equalsIgnoreCase(type);
        }
    }

    /**
     * 日内时段：{@code start} 含、{@code end} 不含（左闭右开）；{@code start} 必须小于 {@code end}，禁止跨午夜（跨零点须拆两段）。
     * {@code end} 允许特例 {@code "24:00"}（= 次日 0 点），{@code start} 不允许。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Window(String start, String end) {
    }

    /** {@code end} 的午夜特例：表示次日 0 点（左闭右开的右端点） */
    public static final String CLOCK_MIDNIGHT_END = "24:00";

    /** 一天的分钟数（数轴端点：24:00 = 1440） */
    public static final int MINUTES_OF_DAY = 1440;

    /**
     * 换算到「当天 0 点起算分钟数轴」上的窗口区间 {@code [startMinute, endMinute)}。
     * {@code "24:00"} 即数轴端点 1440（= 次日 0 点）；格式非法返回 {@code null}。
     */
    public record WindowRange(int startMinute, int endMinute) {

        /** 时刻（当日分钟数）是否落在区间内（左闭右开） */
        public boolean covers(int minuteOfDay) {
            return minuteOfDay >= startMinute && minuteOfDay < endMinute;
        }
    }

    /**
     * 窗口 -&gt; 分钟数轴区间的唯一换算出口：{@code start} 必须是合法 {@code HH:mm}（不接受 24:00），
     * {@code end} 额外接受 {@code "24:00"}；任一端格式非法返回 {@code null}。
     */
    public static WindowRange windowRangeOf(Window window) {
        if (window == null) {
            return null;
        }
        Integer start = clockMinutes(window.start());
        if (start == null) {
            return null;
        }
        Integer end = CLOCK_MIDNIGHT_END.equals(blankToNull(window.end()))
                ? MINUTES_OF_DAY
                : clockMinutes(window.end());
        return end == null ? null : new WindowRange(start, end);
    }

    /** {@code HH:mm} -> 当日分钟数；非法返回 null（手工解析，{@code 24:00} 不在此放行） */
    private static Integer clockMinutes(String value) {
        String text = blankToNull(value);
        if (text == null || text.length() != 5 || text.charAt(2) != ':') {
            return null;
        }
        String hh = text.substring(0, 2);
        String mm = text.substring(3);
        if (!isDigits(hh) || !isDigits(mm)) {
            return null;
        }
        int hours = Integer.parseInt(hh);
        int minutes = Integer.parseInt(mm);
        if (hours > 23 || minutes > 59) {
            return null;
        }
        return hours * 60 + minutes;
    }

    private static boolean isDigits(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (!Character.isDigit(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 价格解析上下文：用量（阶梯用）+ 发生时间（高低峰用）。
     *
     * {@code occurredAt == null} 时高低峰不调整（倍率 1），供无时间来源的调用点使用。
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
            return JsonKit.fromJsonOrThrow(json, BillingConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("config_json must be a valid JSON object", e);
        }
    }

    /** PROGRESSIVE 阶梯是否启用：tierMode 显式为 PROGRESSIVE 且配了档位 */
    public boolean progressive() {
        return TIER_MODE_PROGRESSIVE.equalsIgnoreCase(blankToNull(tierMode)) && tier != null && !tier.isEmpty();
    }

    /**
     * 命中本次请求的附加费总额：触发条件成立即累加。
     *
     * {@code sound} / {@code reference} 来自请求侧生成属性；附加费是常数项，不随用量变化，也不参与阶梯与高低峰。
     */
    public BigDecimal surchargeTotal(boolean sound, boolean reference) {
        if (surcharges == null || surcharges.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Surcharge item : surcharges) {
            if (item == null || item.amount() == null || !item.triggered(sound, reference)) {
                continue;
            }
            total = total.add(item.amount());
        }
        return total;
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
         * 维度键取价（媒体模式的分档单价）：候选键顺序命中 -&gt; {@code defaultUnitPrice} -&gt; fallback，再乘相位倍率。
         *
         * 分辨率价（{@code resolutionPrices}）必须走本入口，与基础价过同一条倍率链。
         */
        BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback);

        /**
         * 多分量场景下单分量的取价：候选键 -&gt; 分量自带默认价 -&gt; fallback，再乘相位倍率。
         *
         * 维度键未命中时回落到分量自己的 {@code video.inputVideoPerSecond} / {@code inputImagePerImage}，
         * 主分量（输出视频）才回落 {@code defaultUnitPrice}。
         */
        BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                         BigDecimal componentDefault,
                                         PricingContext context,
                                         BigDecimal fallback);

        /**
         * PROGRESSIVE 阶梯的单价累加和：{@code Σ(i=1..amount)} 第 i 个单位所属档位的单价。
         *
         * 非 PROGRESSIVE 时返回 {@code null}，调用方回退到「量 × 单价」；返回值为单价域的和，单位基数换算在计算器侧。
         */
        BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback);

        /**
         * 维度分档价的严格取价：规则配了档位表则返回命中价（已乘相位倍率），未命中即抛；
         * 规则未配档位表返回 {@code null}，调用方按「不分档」用默认价，不回落 {@code defaultUnitPrice}。
         *
         * ABSOLUTE 高低峰返回相位自带价；相位缺 {@code unitPrices} 同样视为配置漏项而抛。
         *
         * @param label 维度标识，只进错误文案（便于定位是哪一档缺失）
         * @throws BusinessException 档位表非空但候选键全部未命中
         */
        BigDecimal resolveDimensionPriceStrict(List<String> candidateKeys, PricingContext context, String label);

    }

    /**
     * 维度键取价的唯一实现：按候选键顺序返回第一个命中的价格。
     *
     * 三个方案共用；每个候选键按原样 / 全大写 / 全小写各试一次。
     */
    static BigDecimal dimensionPrice(List<String> candidateKeys, Map<String, BigDecimal> dimensionPrices) {
        if (dimensionPrices == null || candidateKeys == null) {
            return null;
        }
        for (String key : candidateKeys) {
            if (key == null) {
                continue;
            }
            BigDecimal price = dimensionPrices.get(key);
            if (price != null) {
                return price;
            }
            String upper = key.toUpperCase(Locale.ROOT);
            price = dimensionPrices.get(upper);
            if (price != null) {
                return price;
            }
            for (Map.Entry<String, BigDecimal> entry : dimensionPrices.entrySet()) {
                String configuredKey = entry.getKey();
                if (configuredKey != null && configuredKey.toUpperCase(Locale.ROOT).equals(upper)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 维度分档价 + 规则默认价回落：固定价与阶梯价共用的同一段查表与回落。
     *
     * 高低峰 MULTIPLIER 模式也走本方法并在外层乘倍率；ABSOLUTE 模式取相位自带价，不进这里。
     *
     * @return 维度键命中价 -&gt; 规则默认价 -&gt; fallback
     */
    static BigDecimal dimensionPriceOrDefault(List<String> candidateKeys,
                                             Map<String, BigDecimal> dimensionPrices,
                                             BigDecimal defaultUnitPrice,
                                             BigDecimal fallback) {
        BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
        if (matched != null) {
            return matched;
        }
        return defaultUnitPrice != null ? defaultUnitPrice : fallback;
    }

    /**
     * 分量取价：维度键 -&gt; 分量自带默认价 -&gt; 规则默认价 -&gt; fallback。
     *
     * 输入视频秒 / 输入图片张这类自带单价的分量优先于规则默认价，不会被 {@code defaultUnitPrice} 抢先。
     */
    static BigDecimal componentPriceOrDefault(List<String> candidateKeys,
                                              Map<String, BigDecimal> dimensionPrices,
                                              BigDecimal componentDefault,
                                              BigDecimal defaultUnitPrice,
                                              BigDecimal fallback) {
        BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
        if (matched != null) {
            return matched;
        }
        if (componentDefault != null) {
            return componentDefault;
        }
        return defaultUnitPrice != null ? defaultUnitPrice : fallback;
    }

    /**
     * 严格维度取价：档位表为空返回 {@code null}（调用方按「不分档」处理），
     * 档位表非空但候选键全部未命中则抛。
     */
    static BigDecimal requireDimensionPrice(List<String> candidateKeys,
                                            Map<String, BigDecimal> dimensionPrices,
                                            String label) {
        if (dimensionPrices == null || dimensionPrices.isEmpty()) {
            return null;
        }
        if (candidateKeys == null || candidateKeys.isEmpty()) {
            // 请求没声明档位（如视频未带 resolution）→ 没有可比对的档位，不是「档位未命中」
            return null;
        }
        BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
        if (matched != null) {
            return matched;
        }
        String first = candidateKeys.get(0);
        throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "no dimension price configured for " + label + ": " + first
                        + " (configure the tier price or remove the tier table)");
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
            // DIMENSION_TIERED 与厂商定制方案（MiniMax）的取价语义都与固定价完全一致：
            // 多分量编排与维度键拼装在计算器侧，方案值只承担分类标签与校验锚点，故共用同一实现
            case FIXED, DIMENSION_TIERED, VENDOR_MINIMAX ->
                    new FixedPricePolicy(resolved, basePrices, defaultUnitPrice, resolutionPrices);
            case TIERED -> new TieredPricePolicy(basePrices, defaultUnitPrice, tier, progressive(), resolutionPrices);
            case PEAK_OFF_PEAK -> new PeakOffPeakPricePolicy(basePrices, defaultUnitPrice, pricing, effective, resolutionPrices);
        };
    }


    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
