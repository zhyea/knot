package org.chobit.knot.gateway.outage;

/**
 * 摘除状态键的拼装入口。
 *
 * <p>键格式：{@code <prefix>:RULE:<ruleCode>:MODEL:<modelCode>}，
 * 例如 {@code knot:outage:RULE:gpt4o-default:MODEL:gpt-4o}。</p>
 *
 * <p>作用域是<b>规则 × 模型</b>：同一个供应商模型在不同路由规则下独立累计、独立恢复。
 * 模型池目标在候选展开阶段已摊平为具体模型码，故池内单模型异常不会误伤同池其他模型。</p>
 */
public final class OutageKeys {

    private final String prefix;

    /**
     * Constructs a new instance.
     *
     * @param prefix 键前缀，多环境共用同一 Redis 时用于隔离
     */
    public OutageKeys(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 规则 × 模型 的摘除状态键。
     */
    public String of(String ruleCode, String modelCode) {
        return prefix + ":RULE:" + ruleCode + ":MODEL:" + modelCode;
    }

    /**
     * 全量条目的通配模式，管理面列举时使用（Redis {@code SCAN}）。
     */
    public String pattern() {
        return prefix + ":RULE:*";
    }
}
