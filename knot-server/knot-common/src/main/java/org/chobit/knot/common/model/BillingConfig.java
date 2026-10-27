package org.chobit.knot.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.chobit.knot.common.constants.enums.PricingPlanEnum;
import org.chobit.knot.common.pricing.HolidayCalendar;
import org.chobit.knot.common.pricing.PeakOffPeakResolver;
import org.chobit.knot.common.pricing.PhaseDecision;
import org.chobit.knot.common.util.JsonKit;

import java.math.BigDecimal;
import java.time.Instant;
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
 *   "cacheWriteMode": "standard",  // 可选，TOKEN 缓存写方式：standard=单字段 / ttl=5m/1h 分字段
 *   "tier": [                      // pricingPlan=TIERED 时的阶梯档位（from 含、to 含，to 省略表示上不封顶）
 *     { "condition": { "from": 0, "to": 1000000 }, "unitPrices": { "input": 4, "output": 20 } },
 *     { "condition": { "from": 1000001 },            "unitPrices": { "input": 2, "output": 10 } }
 *   ],
 *   "pricing": {                   // pricingPlan=PEAK_OFF_PEAK 时的高低峰倍率（不含 type，方案类型由版本列表达）
 *     "rateMode": "MULTIPLIER", "timezone": "UTC+0",
 *     "phases": [
 *       { "condition": { "weekdays": [1], "windows": [{"start":"01:00","end":"04:00"}],
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
        /**
         * 阶梯累计方式（仅 pricingPlan=TIERED 时生效）：
         * {@code PER_REQUEST}（默认）整笔用量落一档、全量按该档单价；
         * {@code PROGRESSIVE} 逐单位判定所属档位、单价逐单位累加（Vidu Q2 首秒阶梯）。
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

    /** cacheWriteMode 合法取值 */
    public static final Set<String> SUPPORTED_CACHE_WRITE_MODES = Set.of("standard", "ttl");

    /** tierMode 合法取值 */
    public static final String TIER_MODE_PER_REQUEST = "PER_REQUEST";
    public static final String TIER_MODE_PROGRESSIVE = "PROGRESSIVE";
    public static final Set<String> SUPPORTED_TIER_MODES = Set.of(TIER_MODE_PER_REQUEST, TIER_MODE_PROGRESSIVE);

    /** 首期唯一的计价方式：在模式层基础单价上乘相位倍率 */
    private static final String RATE_MODE_MULTIPLIER = "MULTIPLIER";

    /** 独立单价模式：命中相位自身携带 unitPrices，不再对模式层基础价做乘法 */
    private static final String RATE_MODE_ABSOLUTE = "ABSOLUTE";

    /** UTC 偏移白名单，覆盖 UTC-12 至 UTC+14。 */
    public static final Set<String> SUPPORTED_TIMEZONES = java.util.stream.IntStream.rangeClosed(-12, 14)
            .mapToObj(offset -> offset < 0 ? "UTC" + offset : "UTC+" + offset)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

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

    /**
     * DIMENSION_TIERED 合法的维度后缀：{@code 档位} / {@code 档位:AUDIO} /
     * {@code 档位:REFERENCE} / {@code 档位:REFERENCE:AUDIO} 四种。
     *
     * <p>顺序不能自由排列——计算器按「逐级去尾」生成候选链（见
     * {@code VideoBillingModeCalculator#dimensionKeys}），{@code :AUDIO:REFERENCE}
     * 这种写法永远进不了候选链，配了等于没配。</p>
     */
    private static final Set<String> SUPPORTED_DIMENSION_SUFFIXES = Set.of(
            "",
            ":" + DIMENSION_AUDIO,
            ":" + DIMENSION_REFERENCE,
            ":" + DIMENSION_REFERENCE + ":" + DIMENSION_AUDIO);

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
     * <p>业界三种「量」的口径（设计文档 §3.2 / §3.4）：
     * <ul>
     *   <li>{@code OUTPUT}（默认）：量 = 输出视频秒数。Vidu Q3/Q4、豆包走这条。</li>
     *   <li>{@code SUM}：量 = 输入秒 + 输出秒。MiniMax 输入视频按秒走这条。</li>
     *   <li>{@code OUTPUT_PLUS_MAX}：量 = 输出秒 + max(输入秒, 输出秒)，<b>输入为 0 时即输出秒</b>。
     *       一条公式覆盖 Kling 三档条件——无输入=输出；输入≤输出=输出×2；输入&gt;输出=输入+输出。</li>
     * </ul>
     *
     * <p>输入侧分量（{@code inputVideoPerSecond} / {@code inputImagePerImage}）非空时才启用对应分量，
     * 各分量独立计价后求和（MiniMax「输出秒 + 输入秒 + 输入图片张」三路）。
     * 分量单价同样走维度键分档（键前缀 {@link #INPUT_VIDEO_PREFIX} / {@link #INPUT_IMAGE_PREFIX}），
     * 未配置时回落 {@code defaultUnitPrice}。</p>
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
         * <p>⚠ {@code OUTPUT_PLUS_MAX} 的「无输入视频」必须显式判 {@code inputSeconds > 0}：
         * 写成 {@code 输出 + max(输入, 输出)} 时，输入为 0 会算出 {@code 输出 + 输出}，
         * 把 Kling 的「无输入=输出时长」错算成双倍。Kling 三档验算：</p>
         * <pre>
         * 输入 0、输出 5  -> 5              （无输入 = 输出时长）
         * 输入 3、输出 5  -> 5 + 5  = 10    （输入 ≤ 输出 = 输出×2）
         * 输入 8、输出 5  -> 5 + 8  = 13    （输入 > 输出 = 输入+输出）
         * </pre>
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
     * 附加费：与用量无关的常数项（Vidu 音频直出 +15 积分 / 微调推理 +40 积分）。
     *
     * <p>{@code trigger} 决定何时收取：{@code ALWAYS}（恒收）/ {@code AUDIO}（请求侧要了声音）/
     * {@code REFERENCE}（请求侧带了参考输入）。未识别的触发码视为 {@code ALWAYS}——
     * 附加费是「配了就该收」，宁可多收被对账发现，也不要静默漏收。</p>
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
     * 高低峰配置：只描述“价”怎么随时间变化，不含任何模式专属字段。
     *
     * <p>方案类型不写进 JSON（由版本列 {@code pricing_plan} 表达），故此结构无 {@code type} 字段，
     * 避免与版本列双写产生不一致。{@code rateMode} 两种取值：
     * <ul>
     *   <li>{@code MULTIPLIER}（默认）：最终单价 = 模式层基础价 × 命中相位倍率；</li>
     *   <li>{@code ABSOLUTE}：最终单价 = 命中相位自身携带的 {@code unitPrices}，不做乘法（支持高低峰非倍数计费）。</li>
     * </ul>
     * ABSOLUTE 模式下每个 {@link PhaseRule} 必须自带 {@code unitPrices}（分项价格），
     * 简单模式约定把单单价收在 {@code unitPrices.input} 槽位。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pricing(String rateMode, String timezone, List<PhaseRule> phases) {
    }

    /** 相位规则：命中条件 -> 所属相位 + 该相位倍率；ABSOLUTE 模式下额外携带自身单价 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhaseRule(PhaseCondition condition, String phase, BigDecimal multiplier, PriceSet unitPrices) {
    }

    /**
     * 相位命中条件。
     *
     * <p>{@code type} 为 {@code DEFAULT} 时表示兜底低峰，不参与星期/时段判定；
     * {@code weekdays} 为 ISO DayOfWeek 数字（周一=1 ... 周日=7，与 {@link java.time.DayOfWeek#getValue} 同口径）；
     * 其余字段语义见 {@link PeakOffPeakResolver}。
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
     * 日内时段：start 含、end 不含（左闭右开）；start &lt; end，禁止跨午夜（跨零点须拆两段）。
     *
     * <p>{@code end} 允许特例 {@code "24:00"}（= 次日 0 点），用于表达「覆盖到午夜」；
     * {@code start} 不允许 24:00。换算与判定统一走 {@link #windowRangeOf(Window)} 分钟数轴。
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
     *
     * <p>robin 定的口径：窗口一律先换算成分钟数轴再比较，24:00 只是数轴端点 1440（= 明天 0 点），
     * 不在任何判定/校验处散落字符串特判。校验（validateWindows）与判定
     * （{@code PeakOffPeakResolver}）共用本出口；格式非法返回 null。
     */
    public record WindowRange(int startMinute, int endMinute) {

        /** 时刻（当日分钟数）是否落在区间内（左闭右开） */
        public boolean covers(int minuteOfDay) {
            return minuteOfDay >= startMinute && minuteOfDay < endMinute;
        }
    }

    /**
     * 窗口 -> 分钟数轴区间的唯一换算出口。
     *
     * <p>{@code start} 必须是合法 {@code HH:mm}（不接受 24:00）；{@code end} 额外接受
     * {@code "24:00"}。任一端格式非法返回 null。
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
            return JsonKit.fromJsonOrThrow(json, BillingConfig.class);
        } catch (JsonProcessingException e) {
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

    /** 视频分辨率单价：resolutionPrices[resolution]，未配置返回 null */
    public BigDecimal resolutionPrice(String resolution) {
        return resolutionPrices == null ? null : resolutionPrices.get(resolution);
    }

    /** PROGRESSIVE 阶梯是否启用：tierMode 显式为 PROGRESSIVE 且配了档位 */
    public boolean progressive() {
        return TIER_MODE_PROGRESSIVE.equalsIgnoreCase(blankToNull(tierMode)) && tier != null && !tier.isEmpty();
    }

    /**
     * 命中本次请求的附加费总额：触发条件成立即累加。
     *
     * <p>{@code sound} / {@code reference} 来自请求侧生成属性；附加费不随用量变化，
     * 也不参与阶梯与高低峰——它是常数项，与「价」层正交。</p>
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
         * 维度键取价（媒体模式的分档单价）：候选键顺序命中 -&gt; defaultUnitPrice -&gt; fallback，再乘相位倍率。
         *
         * <p>⭐ 这是「分辨率价」唯一的正规入口（2026-10-25）。此前 {@code VideoBillingModeCalculator}
         * 命中 {@code resolutionPrices} 后直接 return，**绕过整个方案层**，导致视频拿不到错峰与阶梯
         * （代码注释自标「风险 ①」）。分档价与基础价必须过同一条倍率链，否则「配了错峰却不生效」
         * 与「错峰只对错了一半的分量生效」两种资损都无从察觉。</p>
         */
        BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback);

        /**
         * 多分量场景下单分量的取价：候选键 -&gt; 分量自带默认价 -&gt; fallback，再乘相位倍率。
         *
         * <p>⭐ 与 {@link #resolveDimensionPrice} 的区别在「维度键未命中时谁兜底」：
         * 主分量（输出视频）回落规则的 {@code defaultUnitPrice}；而输入视频 / 输入图片这类
         * <b>自带分量价</b>的分量必须回落到自己的 {@code video.inputVideoPerSecond} /
         * {@code inputImagePerImage}，<b>不能</b>被 {@code defaultUnitPrice} 抢先——
         * 那会把「输入图片 0.20/张」静默算成「输出视频 0.50/秒」的价（2026-10-25 实测：
         * MiniMax 三路求和被多算 1.5）。两个取价入口因此分开声明，不共用一条回落链。</p>
         */
        BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                         BigDecimal componentDefault,
                                         PricingContext context,
                                         BigDecimal fallback);

        /**
         * PROGRESSIVE 阶梯的单价累加和：{@code Σ(i=1..amount) 第 i 个单位所属档位单价}。
         *
         * <p>只在 {@link #progressive()} 为真时有意义，其余情况返回 {@code null}，
         * 由调用方回退到「量 × 单价」。返回的是<b>单价域</b>的和，单位基数换算仍在计算器侧。</p>
         */
        BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback);
    }

    /**
     * 固定价：基础价格直出（无阶梯、无相位倍率）。
     *
     * <p>{@code plan} 由构建入口按版本列传入，只作<b>分类标签与校验锚点</b>。取价语义与
     * {@link PricingPlanEnum#FIXED} 完全一致，故所有「语义等同固定价」的方案共用这一份实现——
     * MiniMax 与 Kling 两家的多分量编排都在计算器侧，方案值不作为独立的价格算法。
     * 此前 MiniMax 单独复制了一份逐字重复的 record，2026-10-26 加 Kling 时收敛掉。</p>
     */
    private record FixedPricing(PricingPlanEnum plan,
                                PriceSet basePrices,
                                BigDecimal defaultUnitPrice,
                                Map<String, BigDecimal> dimensionPrices) implements PricingPlan {

        @Override
        public PricingPlanEnum plan() {
            return plan == null ? PricingPlanEnum.FIXED : plan;
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

        @Override
        public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
            BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
            return matched != null ? matched : resolveDefaultPrice(context, fallback);
        }

        @Override
        public BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                                BigDecimal componentDefault,
                                                PricingContext context,
                                                BigDecimal fallback) {
            BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
            if (matched != null) {
                return matched;
            }
            return componentDefault != null ? componentDefault : resolveDefaultPrice(context, fallback);
        }

        @Override
        public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
            return null;
        }
    }

    /** 阶梯价：按用量命中档位单价，未命中回退基础价格 */
    private record TieredPricing(PriceSet basePrices,
                                 BigDecimal defaultUnitPrice,
                                 List<TierRule> tier,
                                 boolean progressive,
                                 Map<String, BigDecimal> dimensionPrices) implements PricingPlan {

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

        @Override
        public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
            BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
            return matched != null ? matched : resolveDefaultPrice(context, fallback);
        }

        /**
         * PROGRESSIVE：把用量拆成 {@code amount} 个单位，逐单位判定档位后累加单价。
         *
         * <p>与 {@link #resolveFromTier} 共用一个「按数值命中档位」的判定，
         * 区别只在代入的是<b>单位序号 i</b> 而不是<b>本笔总用量</b>：</p>
         * <pre>
         * Vidu Q2-turbo 720P（第1秒 8，第2秒 10，第3秒起 +10），8 秒：
         *   8 + 10 + 10×6 = 78
         * </pre>
         */
        @Override
        public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
            if (!progressive || amount <= 0) {
                return null;
            }
            BigDecimal total = BigDecimal.ZERO;
            for (long unit = 1; unit <= amount; unit++) {
                BigDecimal price = tierPriceAt(kind, unit);
                if (price == null) {
                    price = resolveDefaultPrice(context, fallback);
                }
                if (price != null) {
                    total = total.add(price);
                }
            }
            return total;
        }

        @Override
        public BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                                BigDecimal componentDefault,
                                                PricingContext context,
                                                BigDecimal fallback) {
            BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
            if (matched != null) {
                return matched;
            }
            return componentDefault != null ? componentDefault : resolveDefaultPrice(context, fallback);
        }

        private BigDecimal tierPriceAt(PriceKind kind, long unitIndex) {
            if (tier == null) {
                return null;
            }
            return resolveFromTier(kind, unitIndex);
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
                                      HolidayCalendar calendar,
                                      Map<String, BigDecimal> dimensionPrices) implements PricingPlan {

        private boolean absolute() {
            return RATE_MODE_ABSOLUTE.equals(blankToNull(pricing == null ? null : pricing.rateMode()));
        }

        @Override
        public PricingPlanEnum plan() {
            return PricingPlanEnum.PEAK_OFF_PEAK;
        }

        @Override
        public BigDecimal resolvePrice(PriceKind kind, PricingContext context, BigDecimal fallback) {
            PhaseDecision decision = PeakOffPeakResolver.resolve(
                    pricing, context == null ? null : context.occurredAt(), calendar);
            if (absolute()) {
                return absolutePrice(decision.matchedRule(), kind, fallback);
            }
            BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
            BigDecimal base = fromBase != null ? fromBase : (defaultUnitPrice != null ? defaultUnitPrice : fallback);
            return base == null ? null : base.multiply(decision.multiplier());
        }

        @Override
        public BigDecimal resolveDefaultPrice(PricingContext context, BigDecimal fallback) {
            PhaseDecision decision = PeakOffPeakResolver.resolve(
                    pricing, context == null ? null : context.occurredAt(), calendar);
            if (absolute()) {
                // 简单模式单单价：ABSOLUTE 相位价格统一收在 input 槽位
                BillingConfig.PhaseRule rule = decision.matchedRule();
                BigDecimal price = rule != null && rule.unitPrices() != null ? rule.unitPrices().input() : null;
                return price != null ? price : fallback;
            }
            BigDecimal base = defaultUnitPrice != null ? defaultUnitPrice : fallback;
            return base == null ? null : base.multiply(decision.multiplier());
        }

        private BigDecimal absolutePrice(BillingConfig.PhaseRule rule, PriceKind kind, BigDecimal fallback) {
            if (rule == null || rule.unitPrices() == null) {
                return fallback;
            }
            BigDecimal price = rule.unitPrices().valueOf(kind);
            return price != null ? price : fallback;
        }

        /**
         * 维度价 × 相位倍率。
         *
         * <p>ABSOLUTE 模式下相位自带单价，维度分档价<b>不</b>参与（相位价已是最终价，
         * 再叠维度价会双重计价）；MULTIPLIER 模式下维度价与普通单价一样乘倍率。</p>
         */
        @Override
        public BigDecimal resolveDimensionPrice(List<String> candidateKeys, PricingContext context, BigDecimal fallback) {
            PhaseDecision decision = PeakOffPeakResolver.resolve(
                    pricing, context == null ? null : context.occurredAt(), calendar);
            if (absolute()) {
                BillingConfig.PhaseRule rule = decision.matchedRule();
                BigDecimal price = rule != null && rule.unitPrices() != null ? rule.unitPrices().input() : null;
                return price != null ? price : fallback;
            }
            BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
            BigDecimal base = matched != null ? matched : (defaultUnitPrice != null ? defaultUnitPrice : fallback);
            return base == null ? null : base.multiply(decision.multiplier());
        }

        /**
         * 分量价 × 相位倍率；ABSOLUTE 模式下取相位自带价（叠加分量默认价会双重计价）。
         */
        @Override
        public BigDecimal resolveComponentPrice(List<String> candidateKeys,
                                                BigDecimal componentDefault,
                                                PricingContext context,
                                                BigDecimal fallback) {
            PhaseDecision decision = PeakOffPeakResolver.resolve(
                    pricing, context == null ? null : context.occurredAt(), calendar);
            if (absolute()) {
                BillingConfig.PhaseRule rule = decision.matchedRule();
                BigDecimal price = rule != null && rule.unitPrices() != null ? rule.unitPrices().input() : null;
                return price != null ? price : fallback;
            }
            BigDecimal matched = dimensionPrice(candidateKeys, dimensionPrices);
            BigDecimal base = matched != null ? matched
                    : (componentDefault != null ? componentDefault
                    : (defaultUnitPrice != null ? defaultUnitPrice : fallback));
            return base == null ? null : base.multiply(decision.multiplier());
        }

        @Override
        public BigDecimal resolveProgressiveSum(PriceKind kind, long amount, PricingContext context, BigDecimal fallback) {
            return null;
        }
    }

    /**
     * 维度键取价的唯一实现：按候选键顺序返回第一个命中的价格。
     *
     * <p>三个方案共用，保证「分档价」在固定价 / 阶梯价 / 高低峰价下走同一条查表口径；
     * 大小写三种写法（原样 / 全大写 / 全小写）都试一次，与旧实现同款宽容度。</p>
     */
    private static BigDecimal dimensionPrice(List<String> candidateKeys, Map<String, BigDecimal> dimensionPrices) {
        if (dimensionPrices == null || candidateKeys == null) {
            return null;
        }
        for (String key : candidateKeys) {
            if (key == null) {
                continue;
            }
            BigDecimal price = dimensionPrices.get(key);
            if (price == null) {
                price = dimensionPrices.get(key.toUpperCase());
            }
            if (price == null) {
                price = dimensionPrices.get(key.toLowerCase());
            }
            if (price != null) {
                return price;
            }
        }
        return null;
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
            // DIMENSION_TIERED 与厂商定制方案（MiniMax / Kling）的取价语义都与固定价完全一致：
            // 多分量编排与维度键拼装在计算器侧，方案值只承担分类标签与校验锚点，故共用同一实现
            case FIXED, DIMENSION_TIERED, VENDOR_MINIMAX, VENDOR_KLING ->
                    new FixedPricing(resolved, basePrices, defaultUnitPrice, resolutionPrices);
            case TIERED -> new TieredPricing(basePrices, defaultUnitPrice, tier, progressive(), resolutionPrices);
            case PEAK_OFF_PEAK -> new PeakOffPeakPricing(basePrices, defaultUnitPrice, pricing, effective, resolutionPrices);
        };
    }

    // ==================== 校验 ====================

    /**
     * 基础结构校验：返回 null 表示合法，否则返回错误描述。
     * 覆盖：价格非负（defaultUnitPrice / basePrices / resolutionPrices）、
     * tier 的 from 必填且非负、from&lt;=to、档位不重叠（开放档位必须是最后一个）。
     */
    public String validate() {
        if (cacheWriteMode != null && !SUPPORTED_CACHE_WRITE_MODES.contains(cacheWriteMode)) {
            return "cacheWriteMode must be standard or ttl";
        }
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
        String videoError = validateVideo(video);
        if (videoError != null) {
            return videoError;
        }
        String tierError = validateTier(tier);
        if (tierError != null) {
            return tierError;
        }
        return validateSurcharges(surcharges);
    }

    /** 视频扩展校验：合成量口径合法、分量单价与免费额度非负 */
    private static String validateVideo(VideoConfig video) {
        if (video == null) {
            return null;
        }
        String mode = blankToNull(video.quantityMode());
        if (mode != null && !VideoConfig.SUPPORTED_QUANTITY_MODES.contains(mode.trim().toUpperCase())) {
            return "video.quantityMode must be OUTPUT, SUM or OUTPUT_PLUS_MAX";
        }
        if (video.inputVideoPerSecond() != null && video.inputVideoPerSecond().signum() < 0) {
            return "video.inputVideoPerSecond cannot be negative";
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
            String trigger = blankToNull(item.trigger());
            if (trigger != null && !Surcharge.SUPPORTED_TRIGGERS.contains(trigger.trim().toUpperCase())) {
                return path + ".trigger must be ALWAYS, AUDIO or REFERENCE";
            }
        }
        return null;
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
            String modeError = validateTierMode();
            return modeError != null ? modeError : validateTierComplete(tier);
        }
        if (resolved == PricingPlanEnum.PEAK_OFF_PEAK) {
            return validatePricing(pricing);
        }
        if (resolved == PricingPlanEnum.VENDOR_MINIMAX) {
            return validateVendorMiniMax();
        }
        if (resolved == PricingPlanEnum.VENDOR_KLING) {
            return validateVendorKling();
        }
        if (resolved == PricingPlanEnum.DIMENSION_TIERED) {
            return validateDimensionTiered();
        }
        // tierMode 只在 TIERED 下有意义：非阶梯方案配了它说明配置意图与方案不一致，拒绝保存
        if (blankToNull(tierMode) != null) {
            return "tierMode is only allowed for TIERED plan";
        }
        return null;
    }

    /** TIERED 专属追加：tierMode 合法（缺省视为 PER_REQUEST） */
    private String validateTierMode() {
        String mode = blankToNull(tierMode);
        if (mode == null) {
            return null;
        }
        return SUPPORTED_TIER_MODES.contains(mode.toUpperCase())
                ? null
                : "tierMode must be PER_REQUEST or PROGRESSIVE";
    }

    /** VENDOR_MINIMAX 专属：必须带厂商定制分量形状（输入视频秒 / 输入图片张数 / 附加费之一），否则配置与类型自相矛盾 */
    private String validateVendorMiniMax() {
        // MiniMax H3 的识别特征是「输入图片按张计费」：视频输入价可由输出档位价回落、免费额度默认 5、
        // 音频输入免费，故只有图片单价是必须显式配置的分量单价
        if (video == null || video.inputImagePerImage() == null) {
            return "VENDOR_MINIMAX requires video.inputImagePerImage (per-image price)";
        }
        return null;
    }

    /**
     * VENDOR_KLING 专属：计费量口径必须显式声明，且不接受 {@code SUM}。
     *
     * <p>⭐ 为什么这条约束必须存在：Kling 的档位价把「有无输入视频」编码进了单价
     * （无输入 1.0/1.5，有输入 0.6/0.9），而「输入秒要不要折进计费量」是<b>另一根轴</b>，
     * 两种口径下同名档位都存在，<b>无法从价表反推</b>：</p>
     * <pre>
     * 在线模型（3.0 / 3.0 Turbo / Omni / O1 / 2.6 / 2.5 Turbo）：计费量 = 输出秒
     * Kling 4.0：计费量 = 输出 + max(输入, 输出)（输入≤输出时即输出×2）
     * </pre>
     *
     * <p>静默按 {@code OUTPUT} 处理会给 4.0 少收一半，静默按 {@code OUTPUT_PLUS_MAX} 处理
     * 会让在线模型多收一倍——两侧都是量级资损，故宁可拒绝保存也不猜。</p>
     *
     * <p>{@code SUM} 被排除：它只在「输入 &gt; 输出」这一档与 Kling 一致，
     * 「输入 ≤ 输出」档会少收一半（官方是输出×2，不是输出+输入）。</p>
     */
    private String validateVendorKling() {
        if (video == null || blankToNull(video.quantityMode()) == null) {
            return "VENDOR_KLING requires video.quantityMode (OUTPUT for online models, OUTPUT_PLUS_MAX for Kling 4.0)";
        }
        if (VideoConfig.QUANTITY_SUM.equals(video.quantityMode().trim().toUpperCase())) {
            return "VENDOR_KLING does not support video.quantityMode=SUM (input seconds are never a flat component)";
        }
        return null;
    }

    /**
     * DIMENSION_TIERED 专属：把「维度键拼错」从<b>静默回落</b>变成<b>拒绝保存</b>。
     *
     * <p>⭐ 本方案的取价算法与 {@link PricingPlanEnum#FIXED} 完全一致（都在
     * {@code FixedPricing}），真正的增量就是这条校验。原因：维度键是<b>自由文本</b>，
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
     *   <li><b>输入秒不得双计</b>：{@code quantityMode=SUM/OUTPUT_PLUS_MAX} 已经把输入秒折进
     *       计费量，再配 {@code video.inputVideoPerSecond} 会同一批秒收两次。</li>
     * </ol>
     */
    private String validateDimensionTiered() {
        if (resolutionPrices == null || resolutionPrices.isEmpty()) {
            return "DIMENSION_TIERED requires resolutionPrices (at least one tier key)";
        }
        boolean tiered = false;
        for (String rawKey : resolutionPrices.keySet()) {
            String key = blankToNull(rawKey);
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
            if (blankToNull(colon < 0 ? body : body.substring(0, colon)) == null) {
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
        if (defaultUnitPrice == null) {
            return "DIMENSION_TIERED requires defaultUnitPrice (fallback when no dimension key matches)";
        }
        return validateInputSecondsNotDoubleCounted();
    }

    /** 输入秒既折进计费量又单独成价 = 同一批秒收两次 */
    private String validateInputSecondsNotDoubleCounted() {
        if (video == null || video.inputVideoPerSecond() == null || blankToNull(video.quantityMode()) == null) {
            return null;
        }
        String mode = upper(video.quantityMode());
        if (VideoConfig.QUANTITY_SUM.equals(mode) || VideoConfig.QUANTITY_OUTPUT_PLUS_MAX.equals(mode)) {
            return "DIMENSION_TIERED: video.inputVideoPerSecond conflicts with video.quantityMode="
                    + mode + " (input seconds would be billed twice)";
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
     * <p>与时区、幅值相关的白名单收在这里：{@code timezone} 使用 UTC 偏移（例如 {@code UTC+8}），
     * {@code holidayPolicy} 首期只允许 {@code OFF_PEAK}，倍率必须落在 {@code (0, 1]}。
     */
    private static String validatePricing(Pricing pricing) {
        if (pricing == null) {
            return "pricing is required for PEAK_OFF_PEAK plan";
        }
        String rateMode = blankToNull(pricing.rateMode());
        if (rateMode == null) {
            rateMode = RATE_MODE_MULTIPLIER;
        }
        if (!RATE_MODE_MULTIPLIER.equals(rateMode) && !RATE_MODE_ABSOLUTE.equals(rateMode)) {
            return "pricing.rateMode must be MULTIPLIER or ABSOLUTE";
        }
        String timezone = blankToNull(pricing.timezone());
        if (timezone == null || !SUPPORTED_TIMEZONES.contains(timezone)) {
            return "pricing.timezone must be a valid UTC offset";
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
        String makeUpPolicy = null;
        boolean absolute = RATE_MODE_ABSOLUTE.equals(rateMode);
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
            if (!SUPPORTED_PHASES.contains(blankToNull(rule.phase()))) {
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
                return item + ".unitPrices." + jsonName(kind) + " cannot be negative";
            }
        }
        return null;
    }

    /** 调休策略归一：缺失按默认 OFF_PEAK */
    private static String normalizedMakeUpPolicy(PhaseCondition condition) {
        String policy = blankToNull(condition.makeUpWorkdayPolicy());
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
            WindowRange range = windowRangeOf(window);
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
