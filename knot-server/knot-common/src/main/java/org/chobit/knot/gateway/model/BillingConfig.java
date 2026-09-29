package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.util.JsonKit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
 *   ]
 * }
 * </pre>
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
        Map<String, BigDecimal> resolutionPrices) {

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

        /** TOKEN 类模式单价：阶梯命中 -&gt; basePrices[kind] -&gt; defaultUnitPrice -&gt; fallback */
        BigDecimal resolvePrice(PriceKind kind, long usageAmount, BigDecimal fallback);

        /** 简单模式/兜底单价：defaultUnitPrice -&gt; fallback */
        BigDecimal resolveDefaultPrice(BigDecimal fallback);
    }

    /** 固定价：基础价格直出 */
    private record FixedPricing(PriceSet basePrices, BigDecimal defaultUnitPrice) implements PricingPlan {

        @Override
        public PricingPlanEnum plan() {
            return PricingPlanEnum.FIXED;
        }

        @Override
        public BigDecimal resolvePrice(PriceKind kind, long usageAmount, BigDecimal fallback) {
            BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
            if (fromBase != null) {
                return fromBase;
            }
            return defaultUnitPrice != null ? defaultUnitPrice : fallback;
        }

        @Override
        public BigDecimal resolveDefaultPrice(BigDecimal fallback) {
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
        public BigDecimal resolvePrice(PriceKind kind, long usageAmount, BigDecimal fallback) {
            BigDecimal fromTier = resolveFromTier(kind, usageAmount);
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
        public BigDecimal resolveDefaultPrice(BigDecimal fallback) {
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
     * 依据版本列 pricing_plan 构建方案领域对象；配置解析失败视为无配置（返回 null 由调用方兜底）。
     */
    public PricingPlan pricingPlan(PricingPlanEnum plan) {
        PricingPlanEnum resolved = plan == null ? PricingPlanEnum.FIXED : plan;
        return switch (resolved) {
            case FIXED -> new FixedPricing(basePrices, defaultUnitPrice);
            case TIERED -> new TieredPricing(basePrices, defaultUnitPrice, tier);
            case PEAK_OFF_PEAK -> throw new IllegalArgumentException(
                    "pricing plan not supported yet: PEAK_OFF_PEAK");
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
}
