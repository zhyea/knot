package org.chobit.knot.gateway.vo.billing;

import java.util.List;

/**
 * 计费能力矩阵：计费模式（量）与进阶定价方案（价）两层能力。
 * 前端据此渲染模式/单位/方案联动，不再自带映射表。
 */
public record BillingCapabilities(List<BillingModeCapability> billingModes,
                                  List<PricingPlanCapability> pricingPlans) {

    /**
     * 计费模式能力：可用计费单位、默认单位、支持的进阶方案。
     */
    public record BillingModeCapability(String code,
                                        List<String> supportedUnits,
                                        String defaultUnit,
                                        List<String> supportedPricingPlans) {
    }

    /**
     * 进阶定价方案能力（仅 {@link org.chobit.knot.gateway.constants.enums.PricingPlanEnum#isAvailable()}
     * 开放的方案；阶段三后三个方案全部开放）。
     */
    public record PricingPlanCapability(String code) {
    }
}
