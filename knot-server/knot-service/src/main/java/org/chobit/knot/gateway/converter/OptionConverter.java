package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.entity.OptionRow;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.meta.LogicalModelMeta;
import org.chobit.knot.gateway.vo.common.meta.ModelMeta;
import org.chobit.knot.gateway.vo.common.meta.ProviderAccountMeta;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 下拉候选 DAL 行 → 对外 VO 的转换。
 *
 * <p>取代原先 {@code OptionsService.toItems/toMeta/toBoolean} 里手写的字符串取键逻辑：
 * 列名与字段名由编译期对齐（{@code OptionsMapper.xml} 的 {@code resultMap} + 本类），
 * 不再出现 {@code r.get("label")} 这类写错不报错的取键。</p>
 *
 * <p>按<b>资源</b>而非按 value 类型拆方法，返回各自 {@code OptionItem<M>}：
 * 有 meta 的三种资源（供应商账户 / 统一模型 / 供应商模型）落到强类型 meta DTO，
 * 其余 8 种无 meta 资源统一 {@code OptionItem<Void>}（meta 恒为 null）。
 * 泛型化后 value 语义（options-refactor-constraints.md 第 0 节第 2 条）由 {@code OptionRow<Long/String>}
 * 与 mapper 返回类型在 DAL 边界守护，本类仅做逐字段归一。</p>
 *
 * <p>两处需显式映射：
 * <ul>
 *   <li>{@code disabled}：SQL 侧 {@code 0/1}（或 {@code case when} 的 int）→ 布尔。
 *       只有 {@code 1} 视为禁用，其余（含 null）均为 null，表示"未禁用"。</li>
 *   <li>{@code meta}：SQL 侧 {@code json_object} 产出的 JSON 文本 → 具体 meta record。
 *       空串/空对象归一为 null（而非空对象），保住"敏感资源 meta 恒为 null"的契约与体积；
 *       解析失败（脏 JSON）由 {@link JsonKit} 吞掉返回 null，避免单个脏值让整个下拉接口 500。</li>
 * </ul>
 */
@Component
public class OptionConverter {

    // ==================== id 型资源（value=Long，无 meta） ====================

    public OptionItem<Void> toUserItem(OptionRow<Long> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toUserItems(List<OptionRow<Long>> rows) {
        return mapRows(rows, this::toUserItem);
    }

    public OptionItem<Void> toDepartmentItem(OptionRow<Long> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toDepartmentItems(List<OptionRow<Long>> rows) {
        return mapRows(rows, this::toDepartmentItem);
    }

    public OptionItem<Void> toAppItem(OptionRow<Long> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toAppItems(List<OptionRow<Long>> rows) {
        return mapRows(rows, this::toAppItem);
    }

    public OptionItem<Void> toRoutingConsumerItem(OptionRow<Long> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toRoutingConsumerItems(List<OptionRow<Long>> rows) {
        return mapRows(rows, this::toRoutingConsumerItem);
    }

    public OptionItem<Void> toRoleItem(OptionRow<Long> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toRoleItems(List<OptionRow<Long>> rows) {
        return mapRows(rows, this::toRoleItem);
    }

    // ==================== code 型 + 有 meta 的资源 ====================

    public OptionItem<ProviderAccountMeta> toProviderAccountItem(OptionRow<String> row) {
        return build(row, parseMeta(row.getMeta(), ProviderAccountMeta.class));
    }

    public List<OptionItem<ProviderAccountMeta>> toProviderAccountItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toProviderAccountItem);
    }

    public OptionItem<LogicalModelMeta> toLogicalModelItem(OptionRow<String> row) {
        return build(row, parseMeta(row.getMeta(), LogicalModelMeta.class));
    }

    public List<OptionItem<LogicalModelMeta>> toLogicalModelItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toLogicalModelItem);
    }

    public OptionItem<ModelMeta> toModelItem(OptionRow<String> row) {
        return build(row, parseMeta(row.getMeta(), ModelMeta.class));
    }

    public List<OptionItem<ModelMeta>> toModelItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toModelItem);
    }

    // ==================== code 型，无 meta 的资源 ====================

    public OptionItem<Void> toModelPoolItem(OptionRow<String> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toModelPoolItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toModelPoolItem);
    }

    public OptionItem<Void> toBillingRuleItem(OptionRow<String> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toBillingRuleItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toBillingRuleItem);
    }

    public OptionItem<Void> toProviderProfileItem(OptionRow<String> row) {
        return build(row, null);
    }

    public List<OptionItem<Void>> toProviderProfileItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toProviderProfileItem);
    }

    // ==================== 私有工具 ====================

    private <M> OptionItem<M> build(OptionRow<?> row, M meta) {
        return new OptionItem<>(row.getValue(), row.getLabel(), row.getCode(),
                toDisabled(row.getDisabled()), meta);
    }

    private static <R, M> List<OptionItem<M>> mapRows(List<R> rows, Function<R, OptionItem<M>> f) {
        return rows == null ? List.of() : rows.stream().map(f).toList();
    }

    /** SQL 侧 disabled（0/1）→ 布尔禁用标记。仅 {@code 1} 为 true，其余（含 null）为 null。 */
    public Boolean toDisabled(Integer disabled) {
        return disabled == null ? null : disabled != 0;
    }

    /**
     * meta 列（{@code json_object} 产物）→ 具体 meta record。空/空白归一为 null；非法 JSON 同样吞为 null，
     * 避免单个脏值让整个下拉接口 500。
     */
    public <T> T parseMeta(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonKit.fromJson(json, type);
    }
}
