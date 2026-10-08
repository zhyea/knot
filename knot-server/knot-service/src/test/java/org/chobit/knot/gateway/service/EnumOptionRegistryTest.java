package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EnumOptionItem;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnumOptionRegistryTest {

    private final EnumOptionRegistry registry = new EnumOptionRegistry();

    // ---------- 取值辅助：数组结构下按 code 精确取 label ----------

    private String labelOf(List<EnumOptionItem> options, Object code) {
        return options.stream()
                .filter(item -> code.equals(item.code()))
                .map(EnumOptionItem::label)
                .findFirst()
                .orElse(null);
    }

    private List<EnumOptionItem> optionsOf(String key) {
        List<EnumOptionItem> options = registry.enumMap().get(key);
        assertNotNull(options, "missing enum: " + key);
        return options;
    }

    private void assertLabel(String key, Object code, String expected) {
        assertEquals(expected, labelOf(optionsOf(key), code),
                "label mismatch for " + key + "." + code);
    }

    @Test
    void exposesOnlyMigratedEnums() {
        Map<String, List<EnumOptionItem>> map = registry.enumMap();

        assertEquals(19, map.size());
        assertTrue(map.containsKey("ModelTypeEnum"));
        assertTrue(map.containsKey("ModelApiProtocolEnum"));
        assertTrue(map.containsKey("BillingModeEnum"));
        assertTrue(map.containsKey("PricingPlanEnum"));
        // EntityStatusEnum 已被 EnabledStatusEnum 取代（五态混用 -> 二态 1/0 + is_deleted）
        assertFalse(map.containsKey("EntityStatusEnum"), "EntityStatusEnum must not be registered");
        assertTrue(map.containsKey("EnabledStatusEnum"));
        assertTrue(map.containsKey("RouteTargetTypeEnum"));
        assertTrue(map.containsKey("BillingUnitEnum"));
        assertTrue(map.containsKey("CurrencyCodeEnum"));
        assertTrue(map.containsKey("QuotaWindowEnum"));
        assertTrue(map.containsKey("ModelPoolSelectionStrategyEnum"));
        assertTrue(map.containsKey("PluginExtensionPoint"));
        assertTrue(map.containsKey("PluginStageCode"));
        assertTrue(map.containsKey("PluginScopeType"));
        assertTrue(map.containsKey("PluginInstanceStatusEnum"));
        assertTrue(map.containsKey("OperationLogStatusEnum"));
        assertTrue(map.containsKey("ScheduledTaskRunStatusEnum"));
        assertTrue(map.containsKey("RoutingTestStatusEnum"));
        assertTrue(map.containsKey("LogicalModelVisibilityEnum"));
        assertTrue(map.containsKey("LogicalModelPublishStatusEnum"));
    }

    @Test
    void billingEnumsExposeLabels() {
        assertEquals(8, optionsOf("BillingModeEnum").size());
        assertLabel("BillingModeEnum", "TOKEN", "Token");
        assertLabel("BillingModeEnum", "FREE", "免费");

        assertEquals(3, optionsOf("PricingPlanEnum").size());
        assertLabel("PricingPlanEnum", "FIXED", "固定价");
        assertLabel("PricingPlanEnum", "TIERED", "阶梯价");
        assertLabel("PricingPlanEnum", "PEAK_OFF_PEAK", "高低峰价");

        assertEquals(2, optionsOf("RouteTargetTypeEnum").size());
        assertLabel("RouteTargetTypeEnum", "MODEL", "模型");
        assertLabel("RouteTargetTypeEnum", "MODEL_POOL", "模型池");

        assertEquals(8, optionsOf("BillingUnitEnum").size());
        assertLabel("BillingUnitEnum", "CUSTOM", "自定义");

        assertEquals(2, optionsOf("CurrencyCodeEnum").size());
        assertLabel("CurrencyCodeEnum", "CNY", "CNY");

        assertEquals(5, optionsOf("QuotaWindowEnum").size());
        assertLabel("QuotaWindowEnum", "MONTH", "每月");
        assertLabel("QuotaWindowEnum", "MINUTE", "每分钟");

        assertEquals(3, optionsOf("ModelPoolSelectionStrategyEnum").size());
        assertLabel("ModelPoolSelectionStrategyEnum", "RANDOM", "随机");

        assertEquals(3, optionsOf("LogicalModelVisibilityEnum").size());
        assertLabel("LogicalModelVisibilityEnum", "PUBLIC", "公开");

        assertEquals(3, optionsOf("LogicalModelPublishStatusEnum").size());
        assertLabel("LogicalModelPublishStatusEnum", 3, "已下架");
        // 发布状态已数字化（NumericEnumOption），字符串 code 不应命中
        assertNull(labelOf(optionsOf("LogicalModelPublishStatusEnum"), "ARCHIVED"));

        assertEquals(2, optionsOf("OperationLogStatusEnum").size());
        assertLabel("OperationLogStatusEnum", 2, "失败");

        assertEquals(3, optionsOf("ScheduledTaskRunStatusEnum").size());
        assertLabel("ScheduledTaskRunStatusEnum", 1, "运行中");

        assertEquals(2, optionsOf("PluginExtensionPoint").size());
        assertLabel("PluginExtensionPoint", "GATEWAY_EXCHANGE", "网关请求处理链路");

        assertEquals(4, optionsOf("PluginInstanceStatusEnum").size());
        assertLabel("PluginInstanceStatusEnum", 2, "生效");
    }

    @Test
    void enabledStatusExposesNumericCodesNotDeleted() {
        List<EnumOptionItem> options = optionsOf("EnabledStatusEnum");

        assertEquals(2, options.size());
        // 数字 code 必须保持 Integer 运行时类型，否则 Jackson 会序列化成字符串
        assertInstanceOf(Integer.class, options.get(0).code());
        assertLabel("EnabledStatusEnum", 1, "启用");
        assertLabel("EnabledStatusEnum", 0, "禁用");
        // 数字 0 不能被误判成「未设置」：它必须能命中 DISABLED
        assertEquals("禁用", labelOf(options, 0));
        // 字符串 "0" 不应命中数字 0（严格类型比较）
        assertNull(labelOf(options, "0"));
        // 删除语义不得再出现在状态选项里
        assertNull(labelOf(options, "DELETED"));
    }

    @Test
    void everyOptionHasCodeAndLabel() {
        registry.enumMap().forEach((key, options) -> {
            assertFalse(options.isEmpty(), "empty options: " + key);
            options.forEach(item -> {
                assertNotNull(item.code(), "null code in " + key);
                assertFalse(item.label().isBlank(), "blank label for " + key + "." + item.code());
            });
        });
    }

    @Test
    void modelTypeEnumMatchesThirteenModelTypes() {
        List<EnumOptionItem> options = optionsOf("ModelTypeEnum");

        assertEquals(13, options.size());
        assertLabel("ModelTypeEnum", "CHAT", "对话");
        assertLabel("ModelTypeEnum", "UTILITY", "工具辅助");
    }

    @Test
    void returnedCollectionsAreImmutable() {
        Map<String, List<EnumOptionItem>> map = registry.enumMap();

        assertThrows(UnsupportedOperationException.class, () -> map.remove("ModelTypeEnum"));
        assertThrows(UnsupportedOperationException.class, () -> map.get("ModelTypeEnum").clear());
    }


    @Test
    void PrintSimpleName() {
        System.out.println(ModelTypeEnum.class.getSimpleName());
    }
}
