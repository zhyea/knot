package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.common.constants.enums.EnumOption;

/**
 * 计费自检八项（代码精确，权威）。语义见 v7 §5 与视频路由调试设计稿 §4.4。
 *
 * <p>自检判定域 = 算术正确性（可确定性）：明细公式、汇总一致、单位换算、
 * 网关账与重算账全等（1 ppm 容差）。语义正确性（规则恰当性、场景相称性）交给 JEV，本区判不了。</p>
 * <p>label 随枚举下发前端（/api/enum-options），不再前端自建第二份项名。</p>
 */
public enum BillingCheckCode implements EnumOption {

    /** 解析用量非全 0（静默归零是最危险的计费缺陷） */
    USAGE_NOT_EMPTY("用量非空"),

    /** totalTokens == input + output（上游未给时由提取器补齐） */
    TOTAL_TOKENS_CONSISTENT("总量一致"),

    /** 明细 quantity × 原始单价 ÷ unitSize(unit) == cost，且汇总 == totalCost */
    COST_FORMULA_MATCH("金额公式"),

    /** 被告账（knot_extend.billing）与同口径重算账全等（1 ppm 容差） */
    GATEWAY_RECOMPUTE_MATCH("被告=重算"),

    /** 计费模式 vs 用量形态（TOKEN 规则配 token 用量，反之亦然） */
    RULE_MODE_MATCH("模式匹配"),

    /** matchedBy 是否为期望层级（EXPLICIT） */
    RULE_SOURCE_EXPECTED("规则来源"),

    /** knot 费用 vs 上游 cost 量级（cost 缺失 → SKIP） */
    UPSTREAM_COST_DEVIATION("上游偏离"),

    /**
     * 异步任务的结算一致性：终态成功 ⟺ 有结算账、终态失败 ⟺ 无结算账。
     *
     * <p>⭐ 它守的是<b>两个相反方向的资损</b>：成功没落账 = 漏收；失败落了账 = 错收。</p>
     */
    ASYNC_SETTLEMENT_CONSISTENCY("异步结算一致");

    private final String label;

    BillingCheckCode(String label) {
        this.label = label;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String label() {
        return label;
    }
}
