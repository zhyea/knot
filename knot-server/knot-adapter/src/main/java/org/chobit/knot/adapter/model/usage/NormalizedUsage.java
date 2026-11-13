package org.chobit.knot.adapter.model.usage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 归一化后的计费视图，即 {@code knot_extend.billing} 的载荷。
 *
 * {@code totalCost} 与明细 {@link NormalizedUsageDetail#cost()} 同为十进制普通字符串契约，绝不出科学计数法。
 * 空值整项省略（{@link JsonInclude.Include#NON_NULL}），只输出有值的字段。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NormalizedUsage(Long totalTokens,
                              String totalCost,
                              String currency,
                              String billingVersion,
                              List<NormalizedUsageDetail> detail,
                              @JsonIgnore String billingMode,
                              @JsonIgnore String pricingPlan,
                              @JsonIgnore String ruleCode) {

    /**
     * 无规则身份的构造口径（管理端被告账反解 / 测试）。
     * 三个归因字段标 {@link JsonIgnore}：{@code knot_extend.billing} 不向调用方暴露内部规则身份，仅数仓侧 {@code BillingViewMsgDto} 读取。
     */
    public NormalizedUsage(Long totalTokens, String totalCost, String currency,
                           String billingVersion, List<NormalizedUsageDetail> detail) {
        this(totalTokens, totalCost, currency, billingVersion, detail, null, null, null);
    }
}
