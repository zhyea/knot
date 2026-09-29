package org.chobit.knot.gateway.vo.billing;

import java.util.List;

/**
 * 计费模式能力项：模式 -> 可用单位 / 默认单位。
 *
 * <p>来源为 {@link org.chobit.knot.gateway.constants.enums.BillingModeEnum}，
 * 后端保存时按同一份定义校验（{@code BillingService#validateRule}），前端据此渲染下拉与联动，
 * 不再自带 unitsByMode / defaultsByMode 映射表。账单明细类型由 billing_mode 与计费计算器固定逻辑确定，
 * 不再下发 defaultItemType。
 */
public record BillingModeCapabilityItem(String code,
                                        List<String> supportedUnits,
                                        String defaultUnit) {
}
