package org.chobit.knot.gateway.vo.routing;

import java.util.List;

/**
 * 单条 JEV 裁判答案（防御式解析产物）。
 *
 * <p>题型取值与 design §6.2 对齐：{@code noul}（布尔）/ {@code choice}（选中项 key + 置信度）/
 * {@code score}（连续刻度 + legend）。任一题答案缺失或解析失败 → {@code status=NOT_RETURNED}，
 * 不整体失败（v7 §6.4 纪律）。</p>
 */
public class JevAnswerItem {

    /** 题名（usage_parse_ok / rule_match_ok / cost_plausible / risk_level） */
    private String key;
    /** 题型（noul / choice / score） */
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

    public JevAnswerItem() {
    }

    public JevAnswerItem(String key, String type, Object value, Double confidence,
                         List<String> legend, String status, String note) {
        this.key = key;
        this.type = type;
        this.value = value;
        this.confidence = confidence;
        this.legend = legend;
        this.status = status;
        this.note = note;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getLegend() {
        return legend;
    }

    public void setLegend(List<String> legend) {
        this.legend = legend;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
