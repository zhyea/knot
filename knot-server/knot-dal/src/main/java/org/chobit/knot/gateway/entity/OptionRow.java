package org.chobit.knot.gateway.entity;

import lombok.Data;

/**
 * 下拉候选投影行（options 专用 DAL 行对象）。
 *
 * <p>{@code OptionsMapper} 的统一返回类型，取代原先的 {@code List<Map<String, Object>>}：
 * 投影列名（value/label/disabled/meta）与本类字段一一对应，MyBatis 直接映射，
 * 列名写错在编译期与冒烟阶段即暴露，不再靠 Service 里手写字符串取键兜底。</p>
 *
 * <p>顶层 <b>没有 {@code code}</b>：业务码属资源语义，由 SQL 侧 {@code json_object} 投到 {@code meta}
 * （如 {@code json_object('consumerCode', c.consumer_code)}），不再单独占一列。</p>
 *
 * <p>与 {@code OptionItem} 的差别：本类是<b> DAL 行</b>（含 {@code disabled} 的 SQL 原始 0/1 语义），
 * {@code OptionItem} 是<b> 对外 VO</b>（{@code disabled} 已是布尔、meta 已是反序列化后的 Map）。
 * 二者转换见 knot-service 的 {@code OptionConverter}。</p>
 *
 * <p>泛型 {@code V} 表示 value 的实际类型：id 型资源为 {@link Long}，code 型资源为 {@link String}。
 * 泛型在此仅作文档与编译期提示——MyBatis 反射填充时按 JDBC 实际类型落值，
 * 与不加泛型的行为完全一致（不存在 unchecked 转换风险）。</p>
 *
 * <p>敏感字段纪律：本类只承载下拉必需的列，SQL 侧绝不 select 出 credential / secretKey /
 * 完整 configJson（见 options-refactor-constraints.md 第 0 节第 6 条）。</p>
 */
@Data
public class OptionRow<V> {

    /** 候选项取值：id 型资源=主键 id（Long），code 型资源=业务码（String）。 */
    private V value;

    /** 展示文本（SQL 层COALESCE 组装好，不在 Java 侧二次拼接）。 */
    private String label;

    /** 是否禁用（停用/ 已删）。SQL 侧输出 0/1，转换层归一为布尔。 */
    private Integer disabled;

    /**
     * 派生属性（SQL 侧 {@code json_object} 生成的 JSON 文本），可为 null。
     * 供应商账户的 baseUrl 已并入此处，不再单独占VO 字段。
     */
    private String meta;
}