package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnumOptionRegistryTest {

    private final EnumOptionRegistry registry = new EnumOptionRegistry();

    @Test
    void exposesOnlyMigratedEnums() {
        Map<String, Map<String, String>> map = registry.enumMap();

        assertEquals(6, map.size());
        assertTrue(map.containsKey("ModelTypeEnum"));
        assertTrue(map.containsKey("ModelApiProtocolEnum"));
        assertTrue(map.containsKey("BillingModeEnum"));
        assertTrue(map.containsKey("PricingPlanEnum"));
        assertTrue(map.containsKey("EntityStatusEnum"));
        assertTrue(map.containsKey("RouteTargetTypeEnum"));
    }

    @Test
    void billingEnumsExposeLabels() {
        Map<String, String> modes = registry.enumMap().get("BillingModeEnum");
        assertEquals(8, modes.size());
        assertEquals("Token", modes.get("TOKEN"));
        assertEquals("免费", modes.get("FREE"));

        Map<String, String> plans = registry.enumMap().get("PricingPlanEnum");
        assertEquals(3, plans.size());
        assertEquals("固定价", plans.get("FIXED"));
        assertEquals("阶梯价", plans.get("TIERED"));
        assertEquals("高低峰价", plans.get("PEAK_OFF_PEAK"));

        Map<String, String> statuses = registry.enumMap().get("EntityStatusEnum");
        assertEquals(5, statuses.size());
        assertEquals("启用", statuses.get("ENABLED"));
        assertEquals("已删除", statuses.get("DELETED"));

        Map<String, String> targets = registry.enumMap().get("RouteTargetTypeEnum");
        assertEquals(2, targets.size());
        assertEquals("模型", targets.get("MODEL"));
        assertEquals("模型池", targets.get("MODEL_POOL"));
    }

    @Test
    void everyOptionHasCodeAndLabel() {
        registry.enumMap().forEach((key, options) -> {
            assertFalse(options.isEmpty(), "empty options: " + key);
            options.forEach((code, label) -> {
                assertFalse(code.isBlank(), "blank code in " + key);
                assertFalse(label.isBlank(), "blank label for " + key + "." + code);
            });
        });
    }

    @Test
    void modelTypeEnumMatchesThirteenModelTypes() {
        Map<String, String> options = registry.enumMap().get("ModelTypeEnum");

        assertEquals(13, options.size());
        assertEquals("对话", options.get("CHAT"));
        assertEquals("工具辅助", options.get("UTILITY"));
    }

    @Test
    void returnedMapsAreImmutable() {
        Map<String, Map<String, String>> map = registry.enumMap();

        assertThrows(UnsupportedOperationException.class, () -> map.remove("ModelTypeEnum"));
        assertThrows(UnsupportedOperationException.class, () -> map.get("ModelTypeEnum").remove("CHAT"));
    }


    @Test
    void PrintSimpleName() {
        System.out.println(ModelTypeEnum.class.getSimpleName());
    }
}
