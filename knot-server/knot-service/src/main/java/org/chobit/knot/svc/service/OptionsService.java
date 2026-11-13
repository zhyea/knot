package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.chobit.knot.svc.converter.OptionConverter;
import org.chobit.knot.dal.entity.OptionRow;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.OptionsMapper;
import org.chobit.knot.common.model.AppOptionQuery;
import org.chobit.knot.common.model.BillingRuleOptionQuery;
import org.chobit.knot.common.model.DepartmentOptionQuery;
import org.chobit.knot.common.model.LogicalModelOptionQuery;
import org.chobit.knot.common.model.ModelOptionQuery;
import org.chobit.knot.common.model.ModelPoolOptionQuery;
import org.chobit.knot.common.model.OptionQuery;
import org.chobit.knot.common.model.ProviderAccountOptionQuery;
import org.chobit.knot.common.model.ProviderProfileOptionQuery;
import org.chobit.knot.common.model.RoleOptionQuery;
import org.chobit.knot.common.model.RoutingConsumerOptionQuery;
import org.chobit.knot.common.model.UserOptionQuery;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.OptionPage;
import org.chobit.knot.svc.vo.common.meta.AppOptionMeta;
import org.chobit.knot.svc.vo.common.meta.DepartmentOptionMeta;
import org.chobit.knot.svc.vo.common.meta.LogicalModelMeta;
import org.chobit.knot.svc.vo.common.meta.ModelMeta;
import org.chobit.knot.svc.vo.common.meta.ProviderAccountMeta;
import org.chobit.knot.svc.vo.common.meta.RoleOptionMeta;
import org.chobit.knot.svc.vo.common.meta.RoutingConsumerOptionMeta;
import org.chobit.knot.svc.vo.common.meta.UserOptionMeta;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 下拉候选（options）服务，统一处理 11 类资源。
 * 核心流程：按 keyword / enabledOnly / 资源过滤检索当前页（PageHelper 追加 limit + count，pageSize 上限 50 由 {@link OptionQuery#effectivePageSize()} 钳制）；
 * 带 {@code values} 时按 value 精确取一次（不分页、不过滤 enabledOnly，仅保留「存在」条件并算 disabled）合并进 list，{@code missingValues} 记未命中项（不存在 / 已删 / 无权限）供前端阻止非法提交。
 * DAL 返回 {@link OptionRow}，本类只做分页 / 合并 / 缺失判定，行→ VO 归一交给 {@link OptionConverter}；敏感字段不返回 secretKey / 凭据 / 完整 configJson，供应商账户 baseUrl 走 {@code meta}。
 */
@Service
public class OptionsService {

    private final OptionsMapper optionsMapper;
    private final OptionConverter optionConverter;

    private static final OptionQuery EMPTY = new OptionQuery(null, null, null, null, null, null);

    public OptionsService(OptionsMapper optionsMapper, OptionConverter optionConverter) {
        this.optionsMapper = optionsMapper;
        this.optionConverter = optionConverter;
    }

    // ==================== 用户（value=username；meta=username；路由规则/应用/消费者均按 username 绑定用户） ====================
    public OptionPage<OptionItem<UserOptionMeta>> listUserUsernameOptions(UserOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listUserUsernameOptions(kw, eo, inc),
                vals -> optionsMapper.listUserUsernameOptionsByValues(vals),
                optionConverter::toUserUsernameItems);
    }

    // ==================== 部门（value=id；meta=deptCode） ====================
    public OptionPage<OptionItem<DepartmentOptionMeta>> listDepartmentOptions(DepartmentOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listDepartmentOptions(kw, eo, inc),
                vals -> optionsMapper.listDepartmentOptionsByValues(vals),
                optionConverter::toDepartmentItems);
    }

    // ==================== 应用（value=id；meta=appCode） ====================
    public OptionPage<OptionItem<AppOptionMeta>> listAppOptions(AppOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listAppOptions(kw, eo, inc),
                vals -> optionsMapper.listAppOptionsByValues(vals),
                optionConverter::toAppItems);
    }

    // ==================== 供应商账户（value=code；meta=ProviderAccountMeta.baseUrl） ====================
    public OptionPage<OptionItem<ProviderAccountMeta>> listProviderAccountOptions(ProviderAccountOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listProviderAccountOptions(kw, eo),
                vals -> optionsMapper.listProviderAccountOptionsByValues(vals),
                optionConverter::toProviderAccountItems);
    }

    // ==================== 统一模型（value=modelCode；meta=LogicalModelMeta） ====================
    public OptionPage<OptionItem<LogicalModelMeta>> listLogicalModelOptions(LogicalModelOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String family = code(query == null ? null : query.modelFamilyCode());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listLogicalModelOptions(kw, eo, inc, family),
                vals -> optionsMapper.listLogicalModelOptionsByValues(vals),
                optionConverter::toLogicalModelItems);
    }

    // ==================== 供应商模型（value=modelCode；meta=ModelMeta） ====================
    public OptionPage<OptionItem<ModelMeta>> listModelOptions(ModelOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String logical = code(query == null ? null : query.logicalModelCode());
        String family = code(query == null ? null : query.modelFamilyCode());
        String account = code(query == null ? null : query.providerAccountCode());
        String preferred = code(query == null ? null : query.preferredLogicalModelCode());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listModelOptions(kw, eo, logical, family, account, preferred),
                vals -> optionsMapper.listModelOptionsByValues(vals),
                optionConverter::toModelItems);
    }

    // ==================== 模型池（value=poolCode） ====================
    public OptionPage<OptionItem<Void>> listModelPoolOptions(ModelPoolOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String logical = code(query == null ? null : query.logicalModelCode());
        String preferred = code(query == null ? null : query.preferredLogicalModelCode());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listModelPoolOptions(kw, eo, inc, logical, preferred),
                vals -> optionsMapper.listModelPoolOptionsByValues(vals),
                optionConverter::toModelPoolItems);
    }

    // ==================== 路由消费者（value=consumerCode；meta=consumerCode；禁 secretKey） ====================
    public OptionPage<OptionItem<RoutingConsumerOptionMeta>> listRoutingConsumerOptions(RoutingConsumerOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listRoutingConsumerOptions(kw, eo),
                vals -> optionsMapper.listRoutingConsumerOptionsByValues(vals),
                optionConverter::toRoutingConsumerItems);
    }

    // ==================== 计费规则（value=code） ====================
    public OptionPage<OptionItem<Void>> listBillingRuleOptions(BillingRuleOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String family = code(query == null ? null : query.modelFamilyCode());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listBillingRuleOptions(kw, eo, family),
                vals -> optionsMapper.listBillingRuleOptionsByValues(vals),
                optionConverter::toBillingRuleItems);
    }

    // ==================== 角色（value=id；meta=roleCode；无启用态，disabled 恒 0） ====================
    public OptionPage<OptionItem<RoleOptionMeta>> listRoleOptions(RoleOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        return assemble(base,
                () -> optionsMapper.listRoleOptions(kw),
                vals -> optionsMapper.listRoleOptionsByValues(vals),
                optionConverter::toRoleItems);
    }

    // ==================== 供应商信息（value=code；无启用态，disabled 恒 0） ====================
    public OptionPage<OptionItem<Void>> listProviderProfileOptions(ProviderProfileOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        return assemble(base,
                () -> optionsMapper.listProviderProfileOptions(kw),
                vals -> optionsMapper.listProviderProfileOptionsByValues(vals),
                optionConverter::toProviderProfileItems);
    }

    // ==================== 共享逻辑 ====================

    private <V, M> OptionPage<OptionItem<M>> assemble(
            OptionQuery base,
            Supplier<List<OptionRow<V>>> paged,
            Function<List<String>, List<OptionRow<V>>> byValues,
            Function<List<OptionRow<V>>, List<OptionItem<M>>> convert) {
        List<String> values = base.values();
        if (values != null && values.size() > OptionQuery.MAX_VALUES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "values 最多 " + OptionQuery.MAX_VALUES + " 项");
        }
        int pn = base.effectivePageNum();
        int ps = base.effectivePageSize();

        List<OptionRow<V>> raw;
        long total;
        try (Page<?> page = PageHelper.startPage(pn, ps)) {
            raw = paged.get();
            total = page.getTotal();
        }

        List<OptionItem<M>> items = convertOrEmpty(convert, raw);
        List<OptionItem<M>> merged = new ArrayList<>(items);
        if (values != null && !values.isEmpty()) {
            Set<String> present = items.stream()
                    .map(oi -> String.valueOf(oi.value()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (OptionItem<M> oi : convertOrEmpty(convert, byValues.apply(values))) {
                if (present.add(String.valueOf(oi.value()))) {
                    merged.add(oi);
                }
            }
        }
        return OptionPage.of(merged, total, pn, ps, computeMissing(values, merged));
    }

    /**
     * 批量转换对 null 入参返回 null，而后续 {@code new ArrayList<>(items)} 会 NPE。
     * MyBatis 实际返回空 List，此处仅为守住"入参 null ⇒ 视作空结果"的边界，与原实现一致。
     */
    private static <V, M> List<OptionItem<M>> convertOrEmpty(
            Function<List<OptionRow<V>>, List<OptionItem<M>>> convert, List<OptionRow<V>> rows) {
        List<OptionItem<M>> converted = convert.apply(rows);
        return converted == null ? List.of() : converted;
    }

    private static String keyword(String kw) {
        if (kw == null) {
            return null;
        }
        String t = kw.trim();
        return t.isEmpty() ? null : t;
    }

    private static String code(String c) {
        if (c == null) {
            return null;
        }
        String t = c.trim();
        return t.isEmpty() ? null : t;
    }

    private static <M> List<String> computeMissing(List<String> values, List<OptionItem<M>> merged) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> present = merged.stream()
                .map(oi -> String.valueOf(oi.value()))
                .collect(Collectors.toSet());
        return values.stream().filter(v -> !present.contains(v)).toList();
    }
}
