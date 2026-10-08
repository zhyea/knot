package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.BillingModeEnum;
import org.chobit.knot.gateway.constants.enums.BillingUnitEnum;
import org.chobit.knot.gateway.constants.enums.CurrencyCodeEnum;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.constants.enums.EnumOption;
import org.chobit.knot.gateway.constants.enums.EnumOptionItem;
import org.chobit.knot.gateway.constants.enums.LogicalModelPublishStatusEnum;
import org.chobit.knot.gateway.constants.enums.LogicalModelVisibilityEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.chobit.knot.gateway.constants.enums.NumericEnumOption;
import org.chobit.knot.gateway.constants.enums.OperationLogStatusEnum;
import org.chobit.knot.gateway.constants.enums.PricingPlanEnum;
import org.chobit.knot.gateway.constants.enums.QuotaWindowEnum;
import org.chobit.knot.gateway.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.gateway.constants.enums.RoutingTestStatusEnum;
import org.chobit.knot.gateway.constants.enums.ScheduledTaskRunStatusEnum;
import org.chobit.knot.gateway.plugin.PluginExtensionPoint;
import org.chobit.knot.gateway.plugin.PluginScopeType;
import org.chobit.knot.gateway.plugin.PluginStageCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the enum options that are maintained in code and exposed to the front end through
 * {@code GET /api/common/enums}.
 *
 * <p>Only enums already migrated out of {@code ks_enum_configs} belong here. Enums still kept in the
 * database stay editable by administrators and are served by {@code /api/system/enums}.</p>
 *
 * <p>响应是 {@code enumKey -> [{code, label}, ...]} 数组，不再是 {@code code -> label} 的 map：
 * JSON object key 必然是字符串，用 map 会让数字 code 在传输层退化成字符串 "1"，
 * 与字符串 code 无法区分（见 {@link EnumOptionItem}）。消费方只有前端 {@code useEnumOptions.ts}。</p>
 */
@Component
public class EnumOptionRegistry {

    /**
     * Front end key of an enum -> its options, in stable declaration order.
     */
    private final Map<String, List<EnumOptionItem>> enumMap;

    public EnumOptionRegistry() {
        Map<String, List<EnumOptionItem>> map = new LinkedHashMap<>();
        put(map, ModelTypeEnum.class.getSimpleName(), ModelTypeEnum.values());
        put(map, ModelApiProtocolEnum.class.getSimpleName(), ModelApiProtocolEnum.values());
        put(map, BillingModeEnum.class.getSimpleName(), BillingModeEnum.values());
        put(map, BillingUnitEnum.class.getSimpleName(), BillingUnitEnum.values());
        put(map, CurrencyCodeEnum.class.getSimpleName(), CurrencyCodeEnum.values());
        put(map, QuotaWindowEnum.class.getSimpleName(), QuotaWindowEnum.values());
        put(map, PricingPlanEnum.class.getSimpleName(), PricingPlanEnum.values());
        putNumeric(map, EnabledStatusEnum.class.getSimpleName(), EnabledStatusEnum.values());
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
     * Returns all exposed enums as {@code enumKey -> [{code, label}, ...]}.
     */
    public Map<String, List<EnumOptionItem>> enumMap() {
        return enumMap;
    }

    /**
     * 字符串 code 枚举。形参取 {@code E[]} 而非 {@code EnumOption[]}：调用点传
     * {@code ModelTypeEnum.values()} 时 E 推断为该枚举，无需构造泛型数组
     * （{@code EnumOption<C>[]} 在 Java 中无法安全创建）。
     */
    private static <E extends Enum<E> & EnumOption> void put(
            Map<String, List<EnumOptionItem>> target, String key, E[] values) {
        List<EnumOptionItem> options = Arrays.stream(values)
                .map(item -> EnumOptionItem.of((EnumOption) item))
                .toList();
        target.put(key, Collections.unmodifiableList(options));
    }

    /**
     * 数值 code 枚举。与 {@link #put} 必须异名——擦除后两者签名相同，同名构成重复方法。
     */
    private static <E extends Enum<E> & NumericEnumOption> void putNumeric(
            Map<String, List<EnumOptionItem>> target, String key, E[] values) {
        List<EnumOptionItem> options = Arrays.stream(values)
                .map(item -> EnumOptionItem.of((NumericEnumOption) item))
                .toList();
        target.put(key, Collections.unmodifiableList(options));
    }
}
