package org.chobit.knot.dal.entity;

import lombok.Data;

/**
 * 下拉候选投影行（options 专用 DAL 行对象），{@code OptionsMapper} 的统一返回类型。
 * 投影列 value / label / disabled / meta 与字段一一对应；顶层无 {@code code}，业务码由 SQL
 * 侧 {@code json_object} 投到 {@code meta}；泛型 {@code V} 为 value 实际类型（{@link Long} / {@link String}）。
 * 只承载下拉必需列，不 select credential / secretKey / 完整 configJson。
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
