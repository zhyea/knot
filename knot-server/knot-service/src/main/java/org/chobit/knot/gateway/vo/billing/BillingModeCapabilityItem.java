package org.chobit.knot.gateway.vo.billing;

import java.util.List;

/**
 * 计费模式能力项：模式 -> 可用单位 / 默认单位 / 默认价格项。
 *
 * <p>来源为 {@link org.chobit.knot.gateway.constants.enums.BillingModeEnum}，
 * 后端保存时按同一份定义校验（{@code BillingService#validateRule}），前端据此渲染下拉与联动，
 * 不再自带 unitsByMode / defaultsByMode 映射表。
 *
 * <p>单位与价格项的展示文案由 ks_enum_configs（billing_unit / billing_item_type）提供。
 */
public record BillingModeCapabilityItem(String code,
                                        List<String> supportedUnits,
                                        String defaultUnit,
                                        String defaultItemType) {
}
