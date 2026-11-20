package org.chobit.knot.svc.service;

import org.chobit.knot.svc.dto.billing.BillingRuleDto;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

/**
 * 版本指纹 {@code buildUniqHash} 的兼容性测试。
 *
 * <p>盯五件事：① 归一化前后仅大小写/空白不同的请求得到同一 hash；
 * ② config_json 仅键顺序/空白不同也得到同一 hash；
 * ③ 指纹只覆盖 5 个版本内容字段——改展示/身份字段不影响 hash；
 * ④ 真正改了计费字段则 hash 变化；
 * ⑤ JSON null 值不阻断指纹生成，且仍按键序规范化。
 */
class BillingRuleVersionHashTest {

    private static final String CONFIG = """
        {"basePrices": {"input": 2, "output": 8}, "defaultUnitPrice": 0.01}
        """;

    private static final String IMAGE_CONFIG_WITH_NULLS = """
        {"defaultUnitPrice": 0.04, "imageResolution": null, "imageQuality": null}
        """;

    private final BillingService service = new BillingService(mock(BillingRuleMapper.class), null);

    @Test
    void shouldIgnoreCaseAndWhitespaceDifferences() {
        String canonical = hash(dto("TOKEN", "FIXED", "USD", "ONE_K_TOKENS", CONFIG));
        String messy = hash(dto(" token ", "fixed", " usd", "One_K_Tokens", CONFIG));

        assertEquals(canonical, messy);
    }

    @Test
    void shouldIgnoreConfigJsonFormattingDifferences() {
        String one = hash(dto("TOKEN", "FIXED", "USD", "ONE_K_TOKENS",
            "{\"basePrices\":{\"input\":2,\"output\":8},\"defaultUnitPrice\":0.01}"));
        String two = hash(dto("TOKEN", "FIXED", "USD", "ONE_K_TOKENS",
            "{ \"defaultUnitPrice\" : 0.01 , \"basePrices\" : { \"output\" : 8 , \"input\" : 2 } }"));

        assertEquals(one, two);
    }

    @Test
    void shouldIgnoreFieldsThatAreNotPartOfVersionContent() {
        String base = hash(dto("TOKEN", "FIXED", "USD", "ONE_K_TOKENS", CONFIG));

        // id / code / 版本编码 / 状态 / 有效期 / 备注 / 派生计数都不参与版本内容判断
        BillingRuleDto sameContentOtherFields = new BillingRuleDto(
            999L,
            "BR-OTHER",
            "FAMILY-X",
            "模型族X",
            "v9",
            "other-hash",
            "TOKEN",
            "FIXED",
            "USD",
            "ONE_K_TOKENS",
            CONFIG,
            false,
            LocalDateTime.of(2030, 1, 1, 0, 0),
            LocalDateTime.of(2031, 1, 1, 0, 0),
            "changed remark",
            42L);

        assertEquals(base, hash(sameContentOtherFields));
    }

    @Test
    void shouldChangeWhenBillingContentChanges() {
        String token = hash(dto("TOKEN", "FIXED", "USD", "ONE_K_TOKENS", CONFIG));
        String request = hash(dto("REQUEST", "FIXED", "USD", "ONE_K_TOKENS", CONFIG));

        assertNotEquals(token, request);
    }

    @Test
    void shouldChangeWhenConfigJsonChanges() {
        String one = hash(dto("TOKEN", "FIXED", "USD", "ONE_K_TOKENS", CONFIG));
        String two = hash(dto("TOKEN", "FIXED", "USD", "ONE_K_TOKENS",
            "{\"basePrices\":{\"input\":3,\"output\":8},\"defaultUnitPrice\":0.01}"));

        assertNotEquals(one, two);
    }

    @Test
    void shouldCanonicalizeConfigJsonContainingNullValues() {
        String one = hash(dto("IMAGE", "FIXED", "USD", "PER_IMAGE", IMAGE_CONFIG_WITH_NULLS));
        String two = hash(dto("IMAGE", "FIXED", "USD", "PER_IMAGE",
            "{ \"imageQuality\": null, \"imageResolution\": null, \"defaultUnitPrice\": 0.04 }"));

        assertEquals(one, two);
    }

    private String hash(BillingRuleDto dto) {
        return service.buildUniqHash(dto);
    }

    private BillingRuleDto dto(String billingMode, String pricingPlan, String currency, String unit, String config) {
        return new BillingRuleDto(
            7L, "BR-TOKEN", null, null,
            "v1", null, billingMode, pricingPlan, currency, unit, config,
            true, LocalDateTime.now(), null, null, 0L);
    }
}
