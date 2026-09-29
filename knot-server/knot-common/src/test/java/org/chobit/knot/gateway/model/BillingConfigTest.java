package org.chobit.knot.gateway.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 契约测试：config_json 描述类的解析、结构校验与价格解析链
 * （ladder 命中 -> basePrices -> defaultUnitPrice -> fallback）。
 */
class BillingConfigTest {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void parseHandlesBlankInvalidAndObject() {
        assertNull(BillingConfig.parse(null));
        assertNull(BillingConfig.parse("  "));
        assertNull(BillingConfig.fromJsonOrNull("not-json"));
        assertThrows(IllegalArgumentException.class, () -> BillingConfig.parse("not-json"));
        assertThrows(IllegalArgumentException.class, () -> BillingConfig.parse("[1,2]"));
        assertNotNull(BillingConfig.parse("{}"));
    }

    @Test
    void priceResolutionFollowsLadderBaseDefaultFallback() {
        BillingConfig config = BillingConfig.parse("""
                {"basePrices":{"input":4,"output":20,"cacheRead":0.2},
                 "ladder":[
                   {"condition":{"from":0,"to":1000000},"unitPrices":{"input":1,"output":5}},
                   {"condition":{"from":1000001},"unitPrices":{"input":2}}
                 ]}
                """);
        assertNotNull(config);
        // basePrices 命中
        assertEquals(0, new BigDecimal("4").compareTo(
                config.resolvePrice(BillingConfig.PriceKind.INPUT, 0, ZERO)));
        // 阶梯命中（第一档）
        assertEquals(0, new BigDecimal("1").compareTo(
                config.resolvePrice(BillingConfig.PriceKind.INPUT, 500_000, ZERO)));
        // 阶梯第二档只配了 input，output 落回 basePrices
        assertEquals(0, new BigDecimal("2").compareTo(
                config.resolvePrice(BillingConfig.PriceKind.INPUT, 2_000_000, ZERO)));
        assertEquals(0, new BigDecimal("20").compareTo(
                config.resolvePrice(BillingConfig.PriceKind.OUTPUT, 2_000_000, ZERO)));
        // 完全未配置 -> fallback
        assertEquals(0, new BigDecimal("9").compareTo(
                config.resolvePrice(BillingConfig.PriceKind.CACHE_WRITE, 100, new BigDecimal("9"))));
        // 阶梯边界：from 与 to 均含
        assertEquals(0, new BigDecimal("1").compareTo(
                config.resolvePrice(BillingConfig.PriceKind.INPUT, 1_000_000, ZERO)));
        assertEquals(0, new BigDecimal("2").compareTo(
                config.resolvePrice(BillingConfig.PriceKind.INPUT, 1_000_001, ZERO)));
    }

    @Test
    void defaultUnitPriceFallsBackForSimpleModes() {
        BillingConfig config = BillingConfig.parse("""
                {"defaultUnitPrice":0.1,"basePrices":{"input":1}}
                """);
        assertEquals(0, new BigDecimal("0.1").compareTo(config.resolveDefaultPrice(ZERO)));
        assertNull(BillingConfig.fromJsonOrNull(null));
        assertNull(BillingConfig.fromJsonOrNull(""));
        // 解析失败按无配置计费：resolve 链由调用方 fallback 兜底
        assertNull(BillingConfig.fromJsonOrNull("{bad json"));
        assertEquals(0, new BigDecimal("7").compareTo(resolveSafely("{bad json", new BigDecimal("7"))));
    }

    @Test
    void validateRejectsNegativePricesAndBadLadder() {
        assertNull(BillingConfig.parse("{\"defaultUnitPrice\":0.5}").validate());
        assertEquals("defaultUnitPrice cannot be negative",
                BillingConfig.parse("{\"defaultUnitPrice\":-1}").validate());
        assertEquals("base price cannot be negative: input",
                BillingConfig.parse("{\"basePrices\":{\"input\":-0.1}}").validate());
        assertEquals("resolution price cannot be negative: 720P",
                BillingConfig.parse("{\"resolutionPrices\":{\"720P\":-1}}").validate());
        assertEquals("ladder price cannot be negative: input",
                BillingConfig.parse("{\"ladder\":[{\"condition\":{\"from\":0},\"unitPrices\":{\"input\":-1}}]}").validate());
        assertEquals("ladder item.condition.from is required",
                BillingConfig.parse("{\"ladder\":[{\"condition\":{\"to\":10}}]}").validate());
        assertEquals("ladder item.condition.from cannot be greater than to",
                BillingConfig.parse("{\"ladder\":[{\"condition\":{\"from\":10,\"to\":1}}]}").validate());
        assertEquals("ladder intervals overlap",
                BillingConfig.parse("""
                        {"ladder":[
                          {"condition":{"from":0,"to":100}},
                          {"condition":{"from":100,"to":200}}
                        ]}
                        """).validate());
        assertEquals("ladder intervals overlap: an open-ended interval must be the last one",
                BillingConfig.parse("""
                        {"ladder":[
                          {"condition":{"from":0}},
                          {"condition":{"from":10}}
                        ]}
                        """).validate());
        assertNull(BillingConfig.parse("""
                {"ladder":[
                  {"condition":{"from":0,"to":1000000}},
                  {"condition":{"from":1000001}}
                ]}
                """).validate());
    }

    @Test
    void resolutionPricesAreTypedAndUnknownFieldsIgnored() {
        BillingConfig config = BillingConfig.parse("""
                {"defaultUnitPrice":0.05,
                 "resolutionPrices":{"720P":0.1,"1080P":0.2},
                 "imageResolution":"1024x1024"}
                """);
        assertEquals(0, new BigDecimal("0.1").compareTo(config.resolutionPrice("720P")));
        assertNull(config.resolutionPrice("480P"));
        Map<String, BigDecimal> prices = config.resolutionPrices();
        assertEquals(2, prices.size());
        // 未知字段（imageResolution）忽略，不影响已知字段解析
        assertEquals(0, new BigDecimal("0.05").compareTo(config.resolveDefaultPrice(ZERO)));
    }

    private static BigDecimal resolveSafely(String json, BigDecimal fallback) {
        BillingConfig config = BillingConfig.fromJsonOrNull(json);
        return config == null ? fallback : config.resolveDefaultPrice(fallback);
    }
}
