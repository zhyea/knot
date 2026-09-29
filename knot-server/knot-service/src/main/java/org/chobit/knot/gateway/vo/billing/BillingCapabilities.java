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
     * 进阶定价方案能力（仅已开放的方案；PEAK_OFF_PEAK 属阶段三，暂不下发）。
     */
    public record PricingPlanCapability(String code) {
    }
}
