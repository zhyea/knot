package org.chobit.knot.svc.service;

import org.chobit.knot.svc.converter.BillingConverter;
import org.chobit.knot.svc.dto.billing.BillingRuleDto;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.svc.service.BillingService;
import org.chobit.knot.svc.vo.billing.PricingPreviewRequest;
import org.chobit.knot.svc.vo.billing.PricingPreviewResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 方案试算接口 {@code POST /api/billing/rules/{id}/preview}（B11）的行为测试。
 *
 * <p>盯三件事：① 判定结果与 {@code PeakOffPeakResolver} 一致（phase/reason/multiplier）；
 * ② 返回的单价是**已乘倍率**的最终价；③ 非高低峰方案不臆造相位字段。
 */
class BillingServicePreviewTest {

    private static final String CONFIG = """
            {
              "defaultUnitPrice": 0.01,
              "basePrices": {"input": 2, "output": 8, "cacheRead": 0.2, "cacheWrite": 2.5,
                             "cacheWrite5m": 2.5, "cacheWrite1h": 5},
              "pricing": {
                "rateMode": "MULTIPLIER",
                "timezone": "UTC+0",
                "phases": [
                  {"phase": "PEAK", "multiplier": 1.5,
                   "condition": {"type": "WEEKDAY_WINDOW", "weekdays": [1],
                                 "windows": [{"start": "01:00", "end": "04:00"}]}},
                  {"phase": "OFF_PEAK", "multiplier": 0.5, "condition": {"type": "DEFAULT"}}
                ]
              }
            }
            """;

    private static final String IMAGE_TOKEN_CONFIG = """
            {
              "image": {
                "tokenPrices": {"inputText": 5, "inputImage": 8, "cacheReadText": 1.25,
                                "cacheReadImage": 2, "outputImage": 30}
              }
            }
            """;

    private static final String IMAGE_TOKEN_CONFIG_WITHOUT_CACHE = """
            {"image": {"tokenPrices": {"inputText": 5, "inputImage": 8, "outputImage": 30}}}
            """;

    @Test
    void shouldReportPeakDecisionAndMultipliedPrices() {
        PricingPreviewResult result = preview("2026-09-28T02:30:00Z", "PEAK_OFF_PEAK");

        assertEquals("TOKEN", result.billingMode());
        assertEquals("PEAK", result.phase());
        assertEquals("PEAK_WINDOW", result.reason());
        assertEquals(0, new BigDecimal("1.5").compareTo(result.multiplier()));
        assertEquals("UTC+0", result.timezone());
        assertEquals("2026-09-28T02:30:00Z", result.occurredAt());

        // 基础价 x 1.5
        assertEquals(0, new BigDecimal("3").compareTo(result.prices().get("input")));
        assertEquals(0, new BigDecimal("12").compareTo(result.prices().get("output")));
        assertEquals(0, new BigDecimal("0.3").compareTo(result.prices().get("cache_read")));
        assertEquals(0, new BigDecimal("3.75").compareTo(result.prices().get("cache_write")));
    }

    @Test
    void shouldFallBackToDefaultPhaseOutsidePeakWindow() {
        PricingPreviewResult result = preview("2026-09-30T02:30:00Z", "PEAK_OFF_PEAK");

        assertEquals("OFF_PEAK", result.phase());
        assertEquals("DEFAULT", result.reason());
        assertEquals(0, new BigDecimal("0.5").compareTo(result.multiplier()));
        assertEquals(0, new BigDecimal("1").compareTo(result.prices().get("input")));
    }

    @Test
    void shouldNotInventPhaseForFixedPlan() {
        PricingPreviewResult result = preview("2026-09-28T02:30:00Z", "FIXED");

        assertNull(result.phase());
        assertNull(result.reason());
        assertNull(result.multiplier());
        // 固定价不受时点影响
        assertEquals(0, new BigDecimal("2").compareTo(result.prices().get("input")));
        assertEquals(0, new BigDecimal("8").compareTo(result.prices().get("output")));
    }

    @Test
    void shouldPreviewImageModalityPricesWhenBilledByToken() {
        PricingPreviewResult result = preview("IMAGE", "1M_TOKENS", "FIXED",
                IMAGE_TOKEN_CONFIG, "2026-09-28T02:30:00Z");

        assertEquals("IMAGE", result.billingMode());
        assertEquals(5, result.prices().size());
        assertEquals(0, new BigDecimal("5").compareTo(result.prices().get("inputText")));
        assertEquals(0, new BigDecimal("8").compareTo(result.prices().get("inputImage")));
        assertEquals(0, new BigDecimal("1.25").compareTo(result.prices().get("cachedInputText")));
        assertEquals(0, new BigDecimal("2").compareTo(result.prices().get("cachedInputImage")));
        assertEquals(0, new BigDecimal("30").compareTo(result.prices().get("outputImage")));
        assertEquals(1_000_000, result.unitSize());
        assertNull(result.phase());
    }

    @Test
    void shouldFallBackCacheModalityPriceToModalityInputPrice() {
        PricingPreviewResult result = preview("IMAGE", "1M_TOKENS", "FIXED",
                IMAGE_TOKEN_CONFIG_WITHOUT_CACHE, "2026-09-28T02:30:00Z");

        assertEquals(0, new BigDecimal("5").compareTo(result.prices().get("cachedInputText")));
        assertEquals(0, new BigDecimal("8").compareTo(result.prices().get("cachedInputImage")));
    }

    @Test
    void shouldKeepSingleDefaultPriceWhenImageBilledByCount() {
        PricingPreviewResult result = preview("IMAGE", "PER_IMAGE", "FIXED",
                "{\"defaultUnitPrice\": 0.3}", "2026-09-28T02:30:00Z");

        assertEquals(1, result.prices().size());
        assertEquals(0, new BigDecimal("0.3").compareTo(result.prices().get("default")));
        assertEquals(1, result.unitSize());
    }

    /**
     * mapper / converter 都走 mock：这里验的是「DTO -> 试算结果」这一段纯逻辑，
     * 不想被 MapStruct 的字段注入绑住。
     */
    private PricingPreviewResult preview(String occurredAt, String pricingPlan) {
        return preview("TOKEN", "1K_TOKENS", pricingPlan, CONFIG, occurredAt);
    }

    private PricingPreviewResult preview(String mode, String unit, String pricingPlan,
                                         String config, String occurredAt) {
        BillingRuleEntity entity = entity(mode, unit, pricingPlan, config);
        BillingRuleMapper mapper = mock(BillingRuleMapper.class);
        when(mapper.getById(7L)).thenReturn(entity);

        BillingRuleDto dto = new BillingRuleDto(
                7L, "BR-TOKEN-PEAK", null, null,
                "v1", null, mode, pricingPlan, "USD", unit, config,
                true, LocalDateTime.now(), null, null, 0L);
        BillingConverter converter = mock(BillingConverter.class);
        when(converter.toRuleDto(entity)).thenReturn(dto);

        BillingService service = new BillingService(mapper, converter);
        return service.previewPricing(7L, new PricingPreviewRequest(occurredAt, null));
    }

    private BillingRuleEntity entity(String mode, String unit, String pricingPlan, String config) {
        BillingRuleEntity entity = new BillingRuleEntity();
        entity.setId(7L);
        entity.setCode("BR-TOKEN-PEAK");
        entity.setBillingMode(mode);
        entity.setPricingPlan(pricingPlan);
        entity.setCurrency("USD");
        entity.setUnit(unit);
        entity.setVersionCode("v1");
        entity.setConfigJson(config);
        return entity;
    }
}
