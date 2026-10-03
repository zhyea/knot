package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.constants.enums.CurrencyCodeEnum;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.LogicalModelPublishStatusEnum;
import org.chobit.knot.gateway.constants.enums.LogicalModelVisibilityEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.chobit.knot.gateway.constants.enums.EnumOption;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.gateway.constants.enums.RoutingTestStatusEnum;
import org.chobit.knot.gateway.constants.enums.ScheduledTaskRunStatusEnum;
import org.chobit.knot.gateway.constants.enums.OperationLogStatusEnum;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginScopeType;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects the enum options that are maintained in code and exposed to the front end through
 * {@code GET /api/common/enums}.
 *
 * <p>Only enums already migrated out of {@code ks_enum_configs} belong here. Enums still kept in the
 * database stay editable by administrators and are served by {@code /api/system/enums}.</p>
 */
@Component
public class EnumOptionRegistry {

    /**
     * Front end key of an enum -> its {@code code -> label} map.
     */
    private final Map<String, Map<String, String>> enumMap;

    public EnumOptionRegistry() {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        put(map, ModelTypeEnum.class.getSimpleName(), ModelTypeEnum.values());
        put(map, ModelApiProtocolEnum.class.getSimpleName(), ModelApiProtocolEnum.values());
        put(map, BillingModeEnum.class.getSimpleName(), BillingModeEnum.values());
        put(map, BillingUnitEnum.class.getSimpleName(), BillingUnitEnum.values());
        put(map, CurrencyCodeEnum.class.getSimpleName(), CurrencyCodeEnum.values());
        put(map, PricingPlanEnum.class.getSimpleName(), PricingPlanEnum.values());
        put(map, EntityStatusEnum.class.getSimpleName(), EntityStatusEnum.values());
        put(map, RouteTargetTypeEnum.class.getSimpleName(), RouteTargetTypeEnum.values());
        put(map, ModelPoolSelectionStrategyEnum.class.getSimpleName(), ModelPoolSelectionStrategyEnum.values());
        put(map, PluginExtensionPoint.class.getSimpleName(), PluginExtensionPoint.values());
        put(map, PluginStageCode.class.getSimpleName(), PluginStageCode.values());
        put(map, PluginScopeType.class.getSimpleName(), PluginScopeType.values());
        put(map, OperationLogStatusEnum.class.getSimpleName(), OperationLogStatusEnum.values());
        put(map, ScheduledTaskRunStatusEnum.class.getSimpleName(), ScheduledTaskRunStatusEnum.values());
        put(map, RoutingTestStatusEnum.class.getSimpleName(), RoutingTestStatusEnum.values());
        put(map, LogicalModelVisibilityEnum.class.getSimpleName(), LogicalModelVisibilityEnum.values());
        put(map, LogicalModelPublishStatusEnum.class.getSimpleName(), LogicalModelPublishStatusEnum.values());
        this.enumMap = Collections.unmodifiableMap(map);
    }

    /**
     * Returns all exposed enums as {@code enumKey -> (code -> label)}.
     */
    public Map<String, Map<String, String>> enumMap() {
        return enumMap;
    }

    private static void put(Map<String, Map<String, String>> target, String key, EnumOption[] values) {
        Map<String, String> options = new LinkedHashMap<>();
        for (EnumOption item : values) {
            options.put(item.code(), item.label());
        }
        target.put(key, Collections.unmodifiableMap(options));
    }
}
