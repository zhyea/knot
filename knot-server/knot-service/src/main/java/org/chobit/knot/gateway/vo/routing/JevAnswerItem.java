package org.chobit.knot.gateway.vo.routing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 单条 JEV 裁判答案（防御式解析产物）。
 *
 * <p>题型取值与 design §6.2 对齐：{@code choice}（选中项 key + 置信度）/
 * {@code noul}（布尔）/ {@code score}（连续刻度 + legend）。当前计费裁判四题统一使用 choice。
 * 任一题答案缺失或解析失败 → {@code status=NOT_RETURNED}，
 * 不整体失败（v7 §6.4 纪律）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JevAnswerItem {

    /** 题名（usage_parse_ok / rule_match_ok / cost_plausible / risk_level） */
    private String key;
    /** 题型（choice / noul / score） */
    private String type;
    /** 答案值：noul→Boolean，choice→String（选中项 key），score→Double */
    private Object value;
    /** 置信度（choice 可空） */
    private Double confidence;
    /** score 图例（可空） */
    private List<String> legend;
    /** 状态：ANSWERED / NOT_RETURNED */
    private String status;
    /** 展示说明（choice 选中项的 criteria 含义；可空） */
    private String note;
}
