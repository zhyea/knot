package org.chobit.knot.svc.vo.routing;

import java.util.List;

/**
 * 路由规则的一个下游调试目标（模型 / 模型池）。
 *
 * <p>{@code protocols} 与 {@code modelFamilies} 都由该目标<b>实际关联的模型</b>推导：
 * 直挂模型取自身，模型池取池内启用模型的并集。两个集合是 {@code presets} 的唯一筛选依据，
 * 故前端切换目标时不再自己过滤，也不会出现「协议能选、预设却是别的族的」。</p>
 *
 * <p>{@code modelFamilies} 为空表示该目标的模型都没绑模型族 —— 此时预设只按协议筛。</p>
 */
public record RoutingDebugTargetItem(String targetType,
                                     Long targetId,
                                     String targetCode,
                                     String targetName,
                                     boolean primary,
                                     List<String> modelFamilies,
                                     List<String> protocols,
                                     List<RoutingDebugPresetItem> presets) {
}
