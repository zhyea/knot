package org.chobit.knot.gateway.model;

/**
 * Token 计费明细行的类型标识：每条明细描述一类 token 用量的计费结果。
 *
 * <p>取值与历史出账字段一一对应（{@link #key()} 即对外序列化字符串），下游对账 / 报表按这些 key 聚合，
 * 不允许随意改名。集中为枚举是为了消除 {@code TokenBillingModeCalculator.assemble()} 里重复的魔法串，
 * 而非新增概念。</p>
 */
public enum TokenUsageDetailKind {

    /** 未命中缓存的输入 token（输入总量扣掉缓存读 / 缓存写后计费） */
    UNCACHED_INPUT("uncachedInput"),
    /** 命中缓存读的 token */
    CACHED_READ("cachedRead"),
    /** 缓存写 token（非 ttl 分档口径，整体计入） */
    CACHED_WRITE("cachedWrite"),
    /** 缓存写 token，5 分钟档（ttl 分档口径） */
    CACHED_WRITE_5M("cachedWrite5m"),
    /** 缓存写 token，1 小时档（ttl 分档口径） */
    CACHED_WRITE_1H("cachedWrite1h"),
    /** 输出 token */
    OUTPUT("output");

    private final String key;

    TokenUsageDetailKind(String key) {
        this.key = key;
    }

    /** 对外序列化用的明细类型字符串 */
    public String key() {
        return key;
    }
}
