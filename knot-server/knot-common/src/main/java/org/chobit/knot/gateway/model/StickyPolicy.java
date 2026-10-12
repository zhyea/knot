package org.chobit.knot.gateway.model;

import org.chobit.knot.gateway.util.JsonKit;

/**
 * 模型池级「粘性路由」策略。
 *
 * <p>背景：模型池 {@code RANDOM} / {@code WEIGHTED} 选择策略每请求重掷，同一会话的多轮请求
 * 会漂移到池内不同供应商账户/模型，导致上游 prompt cache（按账户 + 区域 + 部署命中）全部落空。
 * 粘性路由对请求头携带的粘性键做一致性 hash，把命中目标<b>置顶为池内首选</b>，其余候选仍按
 * 池策略原序排列作为 failover 链——即粘性只改首选，不改故障转移顺序。</p>
 *
 * <p>粘性只在<b>模型池内</b>生效：路由规则的多个目标是主备语义（切换该由故障驱动），
 * 池内模型才是均衡语义（池内模型全部映射同一统一模型，功能等价）。</p>
 *
 * <p>实现为<b>无状态一致性 hash</b>：不记录绑定、没有 TTL、没有失效逻辑，
 * 多节点各自算出完全一致的环，因此不需要共享存储。</p>
 *
 * <p>各字段在<b>紧凑构造器中即归一化</b>——缺省值填充、空白/超长头名回落默认。
 * 因此任何 {@code StickyPolicy} 实例（含 Jackson 反序列化出来的）拿到的都是可直接使用的有效值，
 * 调用方不需要再做空值兜底。</p>
 *
 * <p>存储为 {@code kb_model_pools.sticky_policy}（TEXT/JSON）；该列为空表示未显式配置，
 * 运行时解析为 {@link #DEFAULT}（默认关闭）。</p>
 */
public record StickyPolicy(
        /** 是否启用粘性路由；关闭时池选择完全按 {@code selection_strategy} */
        Boolean enabled,
        /** 取粘性键的请求头名称 */
        String headerName
) {

    /** 默认粘性键请求头 */
    public static final String HEADER_NAME_DEFAULT = "X-Knot-Sticky-Key";

    /** 头名长度上限，超长视为无效输入回落默认（HTTP 头名本身远小于此） */
    public static final int HEADER_NAME_LIMIT = 128;

    /**
     * 默认策略：<b>默认关闭</b>，头名取 {@link #HEADER_NAME_DEFAULT}。
     *
     * <p>粘性是流量塑形（用均衡换缓存命中），与失败重试那种「内 healing」性质不同，
     * 因此必须显式 opt-in。</p>
     */
    public static final StickyPolicy DEFAULT = new StickyPolicy(Boolean.FALSE, HEADER_NAME_DEFAULT);

    /**
     * 归一化：缺省值填充、空白/超长头名回落默认。
     *
     * <p>写成紧凑构造器而非静态工厂，是为了让 Jackson 反序列化出的实例同样是归一化的，
     * 避免出现「字段为 null 的策略实例」流到运行时。</p>
     */
    public StickyPolicy {
        enabled = enabled != null && enabled;
        headerName = normalizeHeaderName(headerName);
    }

    /**
     * 解析持久化的 JSON；空值或非法 JSON 一律退回 {@link #DEFAULT}，不因配置损坏中断请求。
     */
    public static StickyPolicy parse(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT;
        }
        StickyPolicy parsed = JsonKit.fromJson(json, StickyPolicy.class);
        return parsed == null ? DEFAULT : parsed;
    }

    /**
     * 序列化为持久化 JSON；null 策略写出 null（表示该行未配置，运行时走默认）。
     */
    public static String serialize(StickyPolicy policy) {
        return policy == null ? null : JsonKit.toJson(policy);
    }

    /**
     * 是否实际启用粘性路由（字段本身已归一化，不会为 null）。
     */
    public boolean stickied() {
        return enabled;
    }

    /**
     * 头名归一化：空白或超长一律回落默认头名；大小写不敏感是 HTTP 头语义，这里不改写大小写。
     */
    private static String normalizeHeaderName(String value) {
        if (value == null) {
            return HEADER_NAME_DEFAULT;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > HEADER_NAME_LIMIT) {
            return HEADER_NAME_DEFAULT;
        }
        return trimmed;
    }
}
