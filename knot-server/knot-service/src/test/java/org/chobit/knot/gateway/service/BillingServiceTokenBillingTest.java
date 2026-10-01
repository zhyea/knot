package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TOKEN 计费明细（{@code TokenBillingParts}）的行为测试。
 *
 * <p>盯三件事：① 缓存写 token 与成本进入明细；② 普通输入按「输入 - 缓存读 - 缓存写」计费，
 * 不会把缓存写重复按输入价收一遍；③ 总成本 = 输入 + 输出 + 缓存读 + 缓存写。
 */
class BillingServiceTokenBillingTest {

    /**
     * 每 1K token：input=2、output=8、cacheRead=0.2、cacheWrite=2.5。
     * unit = ONE_K_TOKENS -> unitSize = 1000。
     */
    private static final String CONFIG = """
            {"basePrices": {"input": 2, "output": 8, "cacheRead": 0.2, "cacheWrite": 2.5}}
            """;

    @Test
    void shouldBillCacheWriteSeparatelyFromPlainInput() {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 1000);
        usage.put("completion_tokens", 500);
        usage.put("total_tokens", 1500);
        usage.put("prompt_tokens_details", Map.of("cached_tokens", 200, "cache_creation_input_tokens", 300));

        Map<String, Object> detail = detail(usage);
        @SuppressWarnings("unchecked")
        Map<String, Object> parts = (Map<String, Object>) detail.get("usage");

        assertEquals(1000L, ((Number) parts.get("inputTokens")).longValue());
        assertEquals(500L, ((Number) parts.get("outputTokens")).longValue());
        assertEquals(1500L, ((Number) parts.get("totalTokens")).longValue());
        assertEquals(200L, ((Number) parts.get("cacheReadTokens")).longValue());
        assertEquals(300L, ((Number) parts.get("cacheWriteTokens")).longValue());

        // 普通输入 = 1000 - 200 - 300 = 500 -> 500/1000 * 2 = 1.0
        assertEquals(0, new BigDecimal("1.00000000").compareTo((BigDecimal) parts.get("inputCost")));
        // 输出 500/1000 * 8 = 4.0
        assertEquals(0, new BigDecimal("4.00000000").compareTo((BigDecimal) parts.get("outputCost")));
        // 缓存读 200/1000 * 0.2 = 0.04
        assertEquals(0, new BigDecimal("0.04000000").compareTo((BigDecimal) parts.get("cacheReadCost")));
        // 缓存写 300/1000 * 2.5 = 0.75
        assertEquals(0, new BigDecimal("0.75000000").compareTo((BigDecimal) parts.get("cacheWriteCost")));

        assertEquals(0, new BigDecimal("5.79000000").compareTo((BigDecimal) detail.get("totalCost")));
    }

    @Test
    void shouldNotGoNegativeWhenCachedTokensExceedInput() {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 100);
        usage.put("completion_tokens", 0);
        usage.put("prompt_tokens_details", Map.of("cached_tokens", 400, "cache_creation_input_tokens", 500));

        Map<String, Object> detail = detail(usage);
        @SuppressWarnings("unchecked")
        Map<String, Object> parts = (Map<String, Object>) detail.get("usage");

        // 缓存总量超过输入时，普通输入按 0 计，不能出现负成本
        assertEquals(0, new BigDecimal("0.00000000").compareTo((BigDecimal) parts.get("inputCost")));
        // 缓存读 400/1000 * 0.2 = 0.08；缓存写 500/1000 * 2.5 = 1.25
        assertEquals(0, new BigDecimal("1.33000000").compareTo((BigDecimal) detail.get("totalCost")));
    }

    private Map<String, Object> detail(Map<String, Object> usage) {
        ModelEntity model = new ModelEntity();
        model.setId(11L);
        model.setBillingRuleCode("BR-TOKEN");

        BillingRuleEntity rule = new BillingRuleEntity();
        rule.setId(7L);
        rule.setCode("BR-TOKEN");
        rule.setVersionCode("v1");
        rule.setBillingMode("TOKEN");
        rule.setPricingPlan("FIXED");
        rule.setCurrency("USD");
        rule.setUnit("ONE_K_TOKENS");
        rule.setConfigJson(CONFIG);

        ModelMapper modelMapper = mock(ModelMapper.class);
        when(modelMapper.getById(11L)).thenReturn(model);

        BillingRuleMapper ruleMapper = mock(BillingRuleMapper.class);
        // 真实调用带 LocalDateTime.now()，无法用固定值匹配，这里放宽时间参数
        when(ruleMapper.getActiveByRuleCode(eq("BR-TOKEN"), any(LocalDateTime.class))).thenReturn(rule);

        BillingService service = new BillingService(ruleMapper, modelMapper, null);
        return service.calculateUsageDetail(11L, usage);
    }
}
