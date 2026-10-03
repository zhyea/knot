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

        assertEquals(17, map.size());
        assertTrue(map.containsKey("ModelTypeEnum"));
        assertTrue(map.containsKey("ModelApiProtocolEnum"));
        assertTrue(map.containsKey("BillingModeEnum"));
        assertTrue(map.containsKey("PricingPlanEnum"));
        assertTrue(map.containsKey("EntityStatusEnum"));
        assertTrue(map.containsKey("RouteTargetTypeEnum"));
        assertTrue(map.containsKey("BillingUnitEnum"));
        assertTrue(map.containsKey("CurrencyCodeEnum"));
        assertTrue(map.containsKey("ModelPoolSelectionStrategyEnum"));
        assertTrue(map.containsKey("PluginExtensionPoint"));
        assertTrue(map.containsKey("PluginStageCode"));
        assertTrue(map.containsKey("PluginScopeType"));
        assertTrue(map.containsKey("OperationLogStatusEnum"));
        assertTrue(map.containsKey("ScheduledTaskRunStatusEnum"));
        assertTrue(map.containsKey("RoutingTestStatusEnum"));
        assertTrue(map.containsKey("LogicalModelVisibilityEnum"));
        assertTrue(map.containsKey("LogicalModelPublishStatusEnum"));
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

        Map<String, String> units = registry.enumMap().get("BillingUnitEnum");
        assertEquals(8, units.size());
        assertEquals("自定义", units.get("CUSTOM"));

        Map<String, String> currencies = registry.enumMap().get("CurrencyCodeEnum");
        assertEquals(2, currencies.size());
        assertEquals("CNY", currencies.get("CNY"));

        Map<String, String> strategies = registry.enumMap().get("ModelPoolSelectionStrategyEnum");
        assertEquals(3, strategies.size());
        assertEquals("随机", strategies.get("RANDOM"));

        Map<String, String> visibility = registry.enumMap().get("LogicalModelVisibilityEnum");
        assertEquals(3, visibility.size());
        assertEquals("公开", visibility.get("PUBLIC"));

        Map<String, String> publish = registry.enumMap().get("LogicalModelPublishStatusEnum");
        assertEquals(3, publish.size());
        assertEquals("已下架", publish.get("ARCHIVED"));

        Map<String, String> logStatus = registry.enumMap().get("OperationLogStatusEnum");
        assertEquals(2, logStatus.size());
        assertEquals("失败", logStatus.get("FAILURE"));

        Map<String, String> taskStatus = registry.enumMap().get("ScheduledTaskRunStatusEnum");
        assertEquals(3, taskStatus.size());
        assertEquals("运行中", taskStatus.get("RUNNING"));

        Map<String, String> plugins = registry.enumMap().get("PluginExtensionPoint");
        assertEquals(2, plugins.size());
        assertEquals("网关请求处理链路", plugins.get("GATEWAY_EXCHANGE"));
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
