package org.chobit.knot.gateway.outage;

/**
 * 摘除状态键的拼装入口。
 * 键格式 {@code <prefix>:RULE:<ruleCode>:MODEL:<modelCode>}（如 {@code knot:outage:RULE:gpt4o-default:MODEL:gpt-4o}），作用域为「规则 × 模型」，同一模型在不同规则下独立累计、独立恢复；池目标在候选展开阶段摊平为具体模型码。
 */
public final class OutageKeys {

    private final String prefix;

    /**
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
