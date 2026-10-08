package org.chobit.knot.gateway.vo.common;

/**
 * 下拉候选项。
 *
 * <p>{@code value}/{@code label} 为组件唯一必需字段；{@code meta} 只承载下拉确实需要的
 * 非敏感业务信息。严禁返回 credential、secretKey、完整 configJson、审计字段、大段描述
 * （见 options-refactor-constraints.md 第 0 节）。</p>
 *
 * <p>顶层 <b>没有 {@code code}</b>：业务码属资源语义，一律走资源专属的 {@code meta} 字段
 * （如 {@code UserOptionMeta.username} / {@code AppOptionMeta.appCode} /
 * {@code RoutingConsumerOptionMeta.consumerCode}），避免调用方按资源猜测同名顶层字段的含义。
 * 绑定键恒为 {@code value}，业务属性恒读 {@code meta}，二者职责分离。</p>
 *
 * <p>泛型 {@code M} 表示 <b>{@code meta} 的强类型</b>（这是本类泛型化的唯一目的）：每种资源各自的 meta
 * DTO 落在 {@code vo.common.meta} 包（如 {@code ProviderAccountMeta}/{@code LogicalModelMeta}/
 * {@code ModelMeta}/{@code UserOptionMeta}/{@code RoutingConsumerOptionMeta}…），由 {@code OptionConverter}
 * 从 SQL 侧 {@code json_object} 产出的 JSON 文本反序列化得到，
 * 前端契约同步见 {@code knot-front/src/api/options.ts}。</p>
 *
 * <p>{@code value} 是非泛型多态类型 {@link Object}（id 型资源=Long、code 型资源=String，
 * 由调用方与 DAL 边界 {@code OptionRow<V>} 守护，本类不再为 value 泛型）。</p>
 *
 * <p>{@code meta} 承载下拉需要的派生信息，一律走这一个字段，不再按资源扩 VO 字段：
 * 供应商账户的 {@code baseUrl}、供应商模型的 {@code providerName}/{@code modelName}/{@code modelType}/
 * {@code logicalModelCode}、统一模型的 {@code modelType}/{@code modelFamily}/{@code status} 均在此。
 * 全部键由 SQL 侧 {@code json_object} 生成，禁止为某资源新增顶层字段。</p>
 */
public record OptionItem<M>(
        Object value,
        String label,
        Boolean disabled,
        M meta
) {
    public OptionItem(Object value, String label) {
        this(value, label, null, null);
    }
}
