package org.chobit.knot.gateway.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 计费配置 JSON（kb_billing_rule_versions.config_json）价格解析器。
 *
 * <p>config_json 约定结构：
 * <pre>
 * {
 *   "defaultUnitPrice": 0.002,                 // 可选，简单模式的兜底单价
 *   "basePrices": { "input": 4, "output": 20, "cacheRead": 0.2, "cacheWrite": 5 }, // TOKEN 模式分项单价
 *   "ladder": [                                // 可选，阶梯区间（from 含、to 含，to 可省略表示上不封顶）
 *     { "condition": { "from": 0, "to": 1000000 }, "unitPrices": { "input": 4, "output": 20 } },
 *     { "condition": { "from": 1000001 },       "unitPrices": { "input": 2, "output": 10 } }
 *   ]
 * }
 * </pre>
 *
 * <p>价格解析顺序：命中阶梯区间的 unitPrices[key] -> basePrices[key] -> defaultUnitPrice -> fallback。
 * 供 knot-adapter 计费计算器与 knot-service 管理端计费详情两条路径共用，解析基于
 * Jackson + BigDecimal，不做 double 运算。
 */
public final class BillingPriceResolver {

    private BillingPriceResolver() {
    }

    /**
     * 按价格键解析单价（TOKEN 等多价格模式）。
     *
     * @param priceKey     价格键，如 input / output / cacheRead / cacheWrite；null 表示不查 basePrices
     * @param ladderAmount 参与阶梯匹配的用量（如总 token 数），0 或负数不参与阶梯匹配
     * @param fallback     全部未命中时的兜底值
     */
    public static BigDecimal resolvePrice(String configJson, String priceKey, long ladderAmount, BigDecimal fallback) {
        if (configJson == null || configJson.isBlank()) {
            return fallback;
        }
        Map<String, Object> config = parseConfig(configJson);
        if (config == null) {
            return fallback;
        }
        BigDecimal fromLadder = resolveFromLadder(config, priceKey, ladderAmount);
        if (fromLadder != null) {
            return fromLadder;
        }
        BigDecimal fromBase = priceKey == null ? null : decimalOf(nested(config, "basePrices", priceKey));
        if (fromBase != null) {
            return fromBase;
        }
        BigDecimal fromDefault = decimalOf(config.get("defaultUnitPrice"));
        if (fromDefault != null) {
            return fromDefault;
        }
        return fallback;
    }

    /**
     * 简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING 等）单价：defaultUnitPrice，支持阶梯覆盖。
     */
    public static BigDecimal resolveDefaultPrice(String configJson, long ladderAmount, BigDecimal fallback) {
        return resolvePrice(configJson, null, ladderAmount, fallback);
    }

    /** 校验 ladder 结构：必须是数组、区间不重叠、from<=to、价格非负。返回 null 表示合法，否则为错误描述。 */
    public static String validateLadder(Object ladder) {
        if (ladder == null) {
            return null;
        }
        if (!(ladder instanceof List<?> tiers) || tiers.isEmpty()) {
            return "ladder must be a non-empty array";
        }
        BigDecimal previousFrom = null;
        BigDecimal previousTo = null;
        for (Object item : tiers) {
            if (!(item instanceof Map<?, ?> tier)) {
                return "ladder item must be an object";
            }
            Object condition = tier.get("condition");
            if (!(condition instanceof Map<?, ?> range)) {
                return "ladder item.condition must be an object";
            }
            BigDecimal from = decimalOf(range.get("from"));
            BigDecimal to = decimalOf(range.get("to"));
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
            Object unitPrices = tier.get("unitPrices");
            if (unitPrices != null) {
                if (!(unitPrices instanceof Map<?, ?> prices)) {
                    return "ladder item.unitPrices must be an object";
                }
                for (Map.Entry<?, ?> entry : prices.entrySet()) {
                    BigDecimal price = decimalOf(entry.getValue());
                    if (price == null || price.signum() < 0) {
                        return "ladder price cannot be negative: " + entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    private static BigDecimal resolveFromLadder(Map<String, Object> config, String priceKey, long ladderAmount) {
        if (ladderAmount <= 0 || !(config.get("ladder") instanceof List<?> tiers)) {
            return null;
        }
        for (Object item : tiers) {
            if (!(item instanceof Map<?, ?> tier) || !(tier.get("condition") instanceof Map<?, ?> range)) {
                continue;
            }
            BigDecimal from = decimalOf(range.get("from"));
            BigDecimal to = decimalOf(range.get("to"));
            BigDecimal amount = BigDecimal.valueOf(ladderAmount);
            if (from == null || from.compareTo(amount) > 0) {
                continue;
            }
            if (to != null && to.compareTo(amount) < 0) {
                continue;
            }
            if (tier.get("unitPrices") instanceof Map<?, ?> prices) {
                Object matched = priceKey == null ? prices.get("default") : prices.get(priceKey);
                BigDecimal price = decimalOf(matched);
                if (price != null) {
                    return price;
                }
            }
        }
        return null;
    }

    private static Object nested(Map<String, Object> config, String section, String key) {
        Object inner = config.get(section);
        if (inner instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseConfig(String configJson) {
        try {
            Object parsed = JsonKit.mapper().readValue(configJson, Object.class);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal decimalOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
