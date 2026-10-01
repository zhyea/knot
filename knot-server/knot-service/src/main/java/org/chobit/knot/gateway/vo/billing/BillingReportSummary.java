package org.chobit.knot.gateway.vo.billing;

import java.util.List;

/**
 * 计费报表汇总（配置维度）：基于计费规则主数据与当前版本聚合的分布统计。
 * 无用量流水，消耗维度报表待用量落库链路立项后再扩展。
 */
public record BillingReportSummary(long totalRules,
                                   long activeRules,
                                   long inactiveRules,
                                   long activeVersionRules,
                                   long modelCount,
                                   List<ModelFamilyDistribution> byModelFamily,
                                   List<CodeCount> byBillingMode,
                                   List<CodeCount> byPricingPlan,
                                   List<CodeCount> byCurrency) {

    /**
     * 模型族维度分布：规则数与其中启用规则数（modelFamilyCode 为 null 表示未限定族的默认规则）。
     */
    public record ModelFamilyDistribution(String modelFamilyCode,
                                          String modelFamilyName,
                                          long ruleCount,
                                          long activeCount) {
    }

    /**
     * 按 code（计费模式/进阶方案/币种）计数的分布项。
     */
    public record CodeCount(String code, long count) {
    }
}
