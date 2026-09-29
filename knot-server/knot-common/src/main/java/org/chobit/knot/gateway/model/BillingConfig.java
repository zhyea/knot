package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.util.JsonKit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 计费配置（kb_billing_rule_versions.config_json）的描述类。
 *
 * <p>约定结构（价格字段全部具名，不用 Map 承载已知价格）：
 * <pre>
 * {
 *   "defaultUnitPrice": 0.002,     // 可选，简单模式兜底单价
 *   "basePrices": {                // 可选，TOKEN 模式分项单价（与 LadderTier.unitPrices 同构）
 *     "input": 4, "output": 20, "cacheRead": 0.2, "cacheWrite": 5,
 *     "cacheWrite5m": 5, "cacheWrite1h": 5
 *   },
 *   "ladder": [                    // 可选，阶梯区间（from 含、to 含，to 省略表示上不封顶）
 *     { "condition": { "from": 0, "to": 1000000 }, "unitPrices": { "input": 4, "output": 20 } },
 *     { "condition": { "from": 1000001 },            "unitPrices": { "input": 2, "output": 10 } }
 *   ],
 *   "resolutionPrices": { "720P": 0.1, "1080P": 0.2 }  // 可选，视频分辨率-&gt;每秒单价
 * }
 * </pre>
 *
 * <p>价格解析（仅 TOKEN 类模式参与阶梯）：命中阶梯区间的 unitPrices[kind] -&gt; basePrices[kind]
 * -&gt; defaultUnitPrice -&gt; fallback。简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING）不参与阶梯，
 * 只取 defaultUnitPrice。全程 BigDecimal，不做 double 运算；网关计算器与管理端计费详情两条路径共用。
 * 未知 JSON 字段忽略（CUSTOM 模式透传内容由 DB 原样保存，服务端不重写）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BillingConfig(
        BigDecimal defaultUnitPrice,
        PriceSet basePrices,
        List<LadderTier> ladder,
        Map<String, BigDecimal> resolutionPrices) {

    /** 价格种类：对应 config_json 中的具名价格字段 */
    public enum PriceKind {
        INPUT, OUTPUT, CACHE_READ, CACHE_WRITE
    }

    /**
     * 一组具名价格（basePrices 与 ladder[].unitPrices 同构）。
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

    /** 单个阶梯档位：条件区间 + 该档价格 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LadderTier(Condition condition, PriceSet unitPrices) {
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
            return JsonKitHolder.MAPPER.readValue(json, BillingConfig.class);
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

    /** 延迟持有 ObjectMapper，避免 record 静态初始化顺序问题 */
    private static final class JsonKitHolder {
        static final ObjectMapper MAPPER = JsonKit.mapper();
    }

    // ==================== 校验 ====================

    /**
     * 结构校验：返回 null 表示合法，否则返回错误描述。
     * 覆盖：价格非负（defaultUnitPrice / basePrices / ladder.unitPrices / resolutionPrices）、
     * ladder 的 from 必填且非负、from&lt;=to、区间不重叠（开放区间必须是最后一个）。
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
        return validateLadder(ladder);
    }

    private static String validateLadder(List<LadderTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }
        BigDecimal previousFrom = null;
        BigDecimal previousTo = null;
        for (LadderTier tier : tiers) {
            if (tier == null || tier.condition() == null) {
                return "ladder item.condition is required";
            }
            BigDecimal from = tier.condition().from();
            BigDecimal to = tier.condition().to();
            if (from == null) {
                return "ladder item.condition.from is required";
            }
            if (from.signum() < 0) {
                return "ladder item.condition.from cannot be negative";
            }
            if (to != null && from.compareTo(to) > 0) {
                return "ladder item.condition.from cannot be greater than to";
            }
            if (previousFrom != null) {
                if (previousTo == null) {
                    return "ladder intervals overlap: an open-ended interval must be the last one";
                }
                if (from.compareTo(previousTo) <= 0) {
                    return "ladder intervals overlap";
                }
            }
            previousFrom = from;
            previousTo = to;
            if (tier.unitPrices() != null) {
                for (PriceKind kind : PriceKind.values()) {
                    BigDecimal price = tier.unitPrices().valueOf(kind);
                    if (price != null && price.signum() < 0) {
                        return "ladder price cannot be negative: " + jsonName(kind);
                    }
                }
            }
        }
        return null;
    }

    private static String jsonName(PriceKind kind) {
        return switch (kind) {
            case INPUT -> "input";
            case OUTPUT -> "output";
            case CACHE_READ -> "cacheRead";
            case CACHE_WRITE -> "cacheWrite";
        };
    }

    // ==================== 价格解析 ====================

    /**
     * TOKEN 类模式单价：命中阶梯区间的 unitPrices[kind] -&gt; basePrices[kind] -&gt; defaultUnitPrice -&gt; fallback。
     *
     * @param ladderAmount 参与阶梯匹配的用量（如总 token 数），&lt;=0 不参与阶梯匹配
     */
    public BigDecimal resolvePrice(PriceKind kind, long ladderAmount, BigDecimal fallback) {
        BigDecimal fromLadder = resolveFromLadder(kind, ladderAmount);
        if (fromLadder != null) {
            return fromLadder;
        }
        BigDecimal fromBase = basePrices == null ? null : basePrices.valueOf(kind);
        if (fromBase != null) {
            return fromBase;
        }
        if (defaultUnitPrice != null) {
            return defaultUnitPrice;
        }
        return fallback;
    }

    /** 简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING）单价：defaultUnitPrice -&gt; fallback，不参与阶梯 */
    public BigDecimal resolveDefaultPrice(BigDecimal fallback) {
        return defaultUnitPrice != null ? defaultUnitPrice : fallback;
    }

    /** 视频分辨率单价：resolutionPrices[resolution] 由调用方按档位匹配后取值 */
    public BigDecimal resolutionPrice(String resolution) {
        return resolutionPrices == null ? null : resolutionPrices.get(resolution);
    }

    private BigDecimal resolveFromLadder(PriceKind kind, long ladderAmount) {
        if (ladderAmount <= 0 || ladder == null) {
            return null;
        }
        BigDecimal amount = BigDecimal.valueOf(ladderAmount);
        for (LadderTier tier : ladder) {
            if (tier == null || tier.condition() == null || tier.unitPrices() == null) {
                continue;
            }
            BigDecimal from = tier.condition().from();
            BigDecimal to = tier.condition().to();
            if (from == null || from.compareTo(amount) > 0) {
                continue;
            }
            if (to != null && to.compareTo(amount) < 0) {
                continue;
            }
            BigDecimal matched = tier.unitPrices().valueOf(kind);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }
}
