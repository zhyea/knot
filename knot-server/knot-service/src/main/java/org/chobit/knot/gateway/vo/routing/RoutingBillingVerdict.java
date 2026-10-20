package org.chobit.knot.gateway.vo.routing;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * JEV 计费裁判结论（v7 §6 / §9 输出）。
 *
 * <p>统一原则（§6.5）：判定失败绝不阻塞——被告链、影子链与自检照常返回，本对象以
 * {@code available=false + reason} 表达降级，而非抛异常炸抽屉。</p>
 */
@Getter
@Setter
public class RoutingBillingVerdict {

    /** 裁判是否可用（false = 未配置 / 未启用 / 调用失败降级） */
    private boolean available;
    /** 不可用原因（available=false 时非空） */
    private String reason;
    /** 综合结论文本（available=true 时由答案汇总生成） */
    private String summary;
    /** 四条答案（usage_parse_ok / rule_match_ok / cost_plausible / risk_level） */
    private List<JevAnswerItem> answers;
    /** 原始响应（调试用，不含凭据） */
    private String rawResponse;

    public static RoutingBillingVerdict unavailable(String reason) {
        RoutingBillingVerdict v = new RoutingBillingVerdict();
        v.available = false;
        v.reason = reason;
        return v;
    }

    public static RoutingBillingVerdict available(String summary, List<JevAnswerItem> answers) {
        RoutingBillingVerdict v = new RoutingBillingVerdict();
        v.available = true;
        v.summary = summary;
        v.answers = answers;
        return v;
    }
}
