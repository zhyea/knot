package org.chobit.knot.svc.vo.model;

/**
 * 模型族载体（模型管理 → 模型族）。
 *
 * <p>模型族<b>物理上不住在自己的表里</b>：它是 {@code ks_enum_configs(category='model_family')}
 * 的一批行，所以字段名按模型域的语义起了别名（{@code item_code → code}、{@code item_label → name}、
 * {@code is_enabled → enabled}）。别名只在 Converter 里做一次，别在这里再绕回枚举的叫法。</p>
 *
 * <p>{@code usageCount} 是派生列（被统一模型 / 计费规则引用次数），不落库；
 * &gt;0 时禁止删除的判据在 svc，不在本载体。</p>
 */
public record ModelFamilyItem(
        Long id,
        /** 族编码（= 枚举 item_code）：统一模型与计费规则的 model_family 引用该值 */
        String code,
        /** 族显示名（= 枚举 item_label） */
        String name,
        Integer sortOrder,
        Boolean enabled,
        String remark,
        /** 派生：被统一模型 / 计费规则引用次数；大于 0 时禁止删除 */
        Integer usageCount
) {
}
