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

    /**
     * tierMode 取值（{@code PER_REQUEST} 整笔落一档 / {@code PROGRESSIVE} 逐单位累加）。
     *
     * <p>词汇留在这里，<b>白名单</b>（哪个方案允许用哪个）在 {@link BillingConfigSupport}——
     * 前者是数据契约的一部分，后者是校验规则。</p>
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

        /**
         * 维度分档价的<b>严格</b>取价：规则配了档位表 → 命中价（已乘相位倍率）；未命中 → 抛。
         * 规则<b>没配</b>档位表 → 返回 {@code null}，调用方按「不分档」用默认价。
         *
         * <p>⭐ 与 {@link #resolveDimensionPrice} 的差别只有一处：<b>不回落 defaultUnitPrice</b>。
         * 判据是「配了档位表 = 已声明按档位分价」，缺这一档就是配置漏项；静默回落默认价
         * 会让单笔账目算错且无人察觉（2026-10-31 robin 拍板「任何未命中都抛」）。</p>
         *
         * <p>ABSOLUTE 高低峰返回相位自带价——该模式下维度价本就不参与；相位缺
         * {@code unitPrices} 同样视为配置漏项而抛。</p>
         *
         * @param label 维度标识，只进错误文案（便于定位是哪一档缺失）
         * @throws BusinessException 档位表非空但候选键全部未命中
         */
        BigDecimal resolveDimensionPriceStrict(List<String> candidateKeys, PricingContext context, String label);

    }

    /**
     * 维度键取价的唯一实现：按候选键顺序返回第一个命中的价格。
     *
     * <p>三个方案共用，保证「分档价」在固定价 / 阶梯价 / 高低峰价下走同一条查表口径；
     * 大小写三种写法（原样 / 全大写 / 全小写）都试一次，与旧实现同款宽容度。</p>
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
     * 维度分档价 + 规则默认价回落：固定价与阶梯价里逐字相同的那一段，收口到这里。
     *
     * <p>⭐ <b>为什么收在这里而不是各实现里</b>：这两个方法在 {@link FixedPricePolicy} 与
     * {@link TieredPricePolicy} 里曾经逐字重复（diff 差 0 行）。重复不会立刻出错，错在「某天只改了
     * 其中一处」——那正是分档价静默算歪的开端。维度键查表与回落顺序是<b>全类共用的事实</b>，
     * 因此跟 {@link #dimensionPrice} 一起留在这里，而不是各实现各留一份。</p>
     *
     * <p>高低峰价在 <b>MULTIPLIER</b> 模式下走的也是本方法，再在外层乘倍率；
     * <b>ABSOLUTE</b> 模式取相位自带价，根本不进这里。</p>
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
     * <p>⭐ 与 {@link #dimensionPriceOrDefault} 的<b>唯一</b>区别是「维度未命中时谁兜底」：
     * 输入视频秒 / 输入图片张这类分量<b>自带单价</b>，不能被规则默认价抢先——那会把
     * 「输入图片 0.2/张」静默算成「输出视频 0.5/秒」的价（2026-10-25 实测：MiniMax 三路求和被多算 1.5）。</p>
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
     * 档位表非空但候选键全部未命中则<b>抛</b>。
     *
     * <p>为什么「档位表为空」不抛：那不叫「未命中某一档」，而是这条规则压根没声明按档位分价
     * （视频只按秒 / 按次计价是常见形态）。把它也判成未命中，等于所有固定价视频规则一起失效。</p>
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
