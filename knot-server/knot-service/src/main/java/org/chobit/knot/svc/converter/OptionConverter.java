package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.OptionRow;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.meta.AppOptionMeta;
import org.chobit.knot.svc.vo.common.meta.DepartmentOptionMeta;
import org.chobit.knot.svc.vo.common.meta.LogicalModelMeta;
import org.chobit.knot.svc.vo.common.meta.ModelMeta;
import org.chobit.knot.svc.vo.common.meta.ProviderAccountMeta;
import org.chobit.knot.svc.vo.common.meta.RoleOptionMeta;
import org.chobit.knot.svc.vo.common.meta.RoutingConsumerOptionMeta;
import org.chobit.knot.svc.vo.common.meta.UserOptionMeta;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 下拉候选 DAL 行 → 对外 VO 的转换；按资源拆方法，各自返回 {@code OptionItem<M>}。
 *
 * 八种有 meta 的资源落强类型 record，模型池 / 计费规则 / 供应商信息统一 {@code OptionItem<Void>}（meta 恒为 null）。
 * id 型资源的业务码由 SQL 的 {@code json_object} 投到 meta，经 {@link #parseMeta} 反序列化。
 * {@code disabled} 仅 {@code 1} 为禁用；{@code meta} 空串 / 空对象归一为 null，非法 JSON 吞为 null。
 */
@Component
public class OptionConverter {

    // ==================== id 型资源（value=Long；业务码走 meta） ====================

    /** 用户下拉按 username 取值（跨模块绑定存 code 不存 id），故用户只有 String 型 value。 */
    public OptionItem<UserOptionMeta> toUserUsernameItem(OptionRow<String> row) {
        return build(row, parseMeta(row.getMeta(), UserOptionMeta.class));
    }

    public List<OptionItem<UserOptionMeta>> toUserUsernameItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toUserUsernameItem);
    }

    public OptionItem<DepartmentOptionMeta> toDepartmentItem(OptionRow<Long> row) {
        return build(row, parseMeta(row.getMeta(), DepartmentOptionMeta.class));
    }

    public List<OptionItem<DepartmentOptionMeta>> toDepartmentItems(List<OptionRow<Long>> rows) {
        return mapRows(rows, this::toDepartmentItem);
    }

    public OptionItem<AppOptionMeta> toAppItem(OptionRow<Long> row) {
        return build(row, parseMeta(row.getMeta(), AppOptionMeta.class));
    }

    public List<OptionItem<AppOptionMeta>> toAppItems(List<OptionRow<Long>> rows) {
        return mapRows(rows, this::toAppItem);
    }

    public OptionItem<RoutingConsumerOptionMeta> toRoutingConsumerItem(OptionRow<String> row) {
        return build(row, parseMeta(row.getMeta(), RoutingConsumerOptionMeta.class));
    }

    public List<OptionItem<RoutingConsumerOptionMeta>> toRoutingConsumerItems(List<OptionRow<String>> rows) {
        return mapRows(rows, this::toRoutingConsumerItem);
    }

    public OptionItem<RoleOptionMeta> toRoleItem(OptionRow<Long> row) {
        return build(row, parseMeta(row.getMeta(), RoleOptionMeta.class));
    }

    public List<OptionItem<RoleOptionMeta>> toRoleItems(List<OptionRow<Long>> rows) {
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
        return new OptionItem<>(row.getValue(), row.getLabel(),
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
     * meta 列（{@code json_object} 产物）→ 具体 meta record。空 / 空白归一为 null，非法 JSON 同样吞为 null。
     */
    public <T> T parseMeta(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JsonKit.fromJson(json, type);
    }
}
