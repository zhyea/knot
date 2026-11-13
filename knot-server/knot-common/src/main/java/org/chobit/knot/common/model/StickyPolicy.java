package org.chobit.knot.common.model;

import org.chobit.knot.common.util.JsonKit;

/**
 * 模型池级「粘性路由」策略：对请求头携带的粘性键做一致性 hash，把命中目标置顶为池内首选，
 * 其余候选仍按池策略原序排列作为 failover 链（只改首选，不改故障转移顺序）；仅在模型池内生效。
 * 实现为无状态一致性 hash，不记录绑定、无 TTL、不需要共享存储，多节点各自算出同一个环。
 * 字段在紧凑构造器中即归一化；存储为 {@code kb_model_pools.sticky_policy}，列为空时取 {@link #DEFAULT}（默认关闭）。
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
     * 默认策略：默认关闭，头名取 {@link #HEADER_NAME_DEFAULT}。
     */
    public static final StickyPolicy DEFAULT = new StickyPolicy(Boolean.FALSE, HEADER_NAME_DEFAULT);

    /**
     * 归一化：缺省值填充、空白/超长头名回落默认；由紧凑构造器执行，Jackson 反序列化的实例同样生效。
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
