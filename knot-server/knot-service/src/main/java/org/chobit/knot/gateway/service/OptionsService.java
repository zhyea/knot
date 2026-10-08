package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.OptionsMapper;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.model.AppOptionQuery;
import org.chobit.knot.gateway.model.BillingRuleOptionQuery;
import org.chobit.knot.gateway.model.DepartmentOptionQuery;
import org.chobit.knot.gateway.model.LogicalModelOptionQuery;
import org.chobit.knot.gateway.model.ModelOptionQuery;
import org.chobit.knot.gateway.model.ModelPoolOptionQuery;
import org.chobit.knot.gateway.model.OptionQuery;
import org.chobit.knot.gateway.model.ProviderAccountOptionQuery;
import org.chobit.knot.gateway.model.ProviderProfileOptionQuery;
import org.chobit.knot.gateway.model.RoleOptionQuery;
import org.chobit.knot.gateway.model.RoutingConsumerOptionQuery;
import org.chobit.knot.gateway.model.UserOptionQuery;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 下拉候选（options）服务：P0 九类 + 阶段四扩的两类（角色、供应商信息）= 11 类资源。
 * 统一契约见 options-refactor-constraints.md。
 *
 * <p>核心流程（每资源一致）：
 * <ol>
 *   <li>按 keyword / enabledOnly / 资源过滤检索当前页（PageHelper 追加 limit + count）；
 *       pageSize 服务端上限 50 由 {@link OptionQuery#effectivePageSize()} 钳制。</li>
 *   <li>若带 {@code values}（已选项回显），再按 value 精确取一次（不分页、不过滤 enabledOnly，
 *       仅保留「存在」条件并算 disabled），合并进 list —— 使「已选项不在第一页」也能正确回显。</li>
 *   <li>{@code missingValues} = 请求 values 中未命中的项（不存在 / 已删 / 无权限），前端据此阻止非法提交。</li>
 * </ol>
 *
 * <p>敏感字段：路由消费者、供应商账户 options 绝不返回 secretKey / 凭据 / 完整 configJson（见约束 R6）。</p>
 */
@Service
public class OptionsService {

    private final OptionsMapper optionsMapper;

    private static final OptionQuery EMPTY = new OptionQuery(null, null, null, null, null, null);

    public OptionsService(OptionsMapper optionsMapper) {
        this.optionsMapper = optionsMapper;
    }

    // ==================== 用户（value=id） ====================
    public OptionPage<OptionItem> listUserOptions(UserOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listUserOptions(kw, eo, inc),
                vals -> optionsMapper.listUserOptionsByValues(vals));
    }

    // ==================== 部门（value=id） ====================
    public OptionPage<OptionItem> listDepartmentOptions(DepartmentOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listDepartmentOptions(kw, eo, inc),
                vals -> optionsMapper.listDepartmentOptionsByValues(vals));
    }

    // ==================== 应用（value=id） ====================
    public OptionPage<OptionItem> listAppOptions(AppOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listAppOptions(kw, eo, inc),
                vals -> optionsMapper.listAppOptionsByValues(vals));
    }

    // ==================== 供应商账户（value=code） ====================
    public OptionPage<OptionItem> listProviderAccountOptions(ProviderAccountOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listProviderAccountOptions(kw, eo),
                vals -> optionsMapper.listProviderAccountOptionsByValues(vals));
    }

    // ==================== 统一模型（value=modelCode） ====================
    public OptionPage<OptionItem> listLogicalModelOptions(LogicalModelOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String family = code(query == null ? null : query.modelFamilyCode());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listLogicalModelOptions(kw, eo, inc, family),
                vals -> optionsMapper.listLogicalModelOptionsByValues(vals));
    }

    // ==================== 供应商模型（value=modelCode） ====================
    public OptionPage<OptionItem> listModelOptions(ModelOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String logical = code(query == null ? null : query.logicalModelCode());
        String family = code(query == null ? null : query.modelFamilyCode());
        String account = code(query == null ? null : query.providerAccountCode());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listModelOptions(kw, eo, logical, family, account),
                vals -> optionsMapper.listModelOptionsByValues(vals));
    }

    // ==================== 模型池（value=poolCode） ====================
    public OptionPage<OptionItem> listModelPoolOptions(ModelPoolOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String logical = code(query == null ? null : query.logicalModelCode());
        boolean eo = base.effectiveEnabledOnly();
        boolean inc = base.effectiveIncludeDeleted();
        return assemble(base,
                () -> optionsMapper.listModelPoolOptions(kw, eo, inc, logical),
                vals -> optionsMapper.listModelPoolOptionsByValues(vals));
    }

    // ==================== 路由消费者（value=id；禁 secretKey） ====================
    public OptionPage<OptionItem> listRoutingConsumerOptions(RoutingConsumerOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listRoutingConsumerOptions(kw, eo),
                vals -> optionsMapper.listRoutingConsumerOptionsByValues(vals));
    }

    // ==================== 计费规则（value=code） ====================
    public OptionPage<OptionItem> listBillingRuleOptions(BillingRuleOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        String family = code(query == null ? null : query.modelFamilyCode());
        boolean eo = base.effectiveEnabledOnly();
        return assemble(base,
                () -> optionsMapper.listBillingRuleOptions(kw, eo, family),
                vals -> optionsMapper.listBillingRuleOptionsByValues(vals));
    }

    // ==================== 角色（value=id；无启用态，disabled 恒 0） ====================
    public OptionPage<OptionItem> listRoleOptions(RoleOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        return assemble(base,
                () -> optionsMapper.listRoleOptions(kw),
                vals -> optionsMapper.listRoleOptionsByValues(vals));
    }

    // ==================== 供应商信息（value=code；无启用态，disabled 恒 0） ====================
    public OptionPage<OptionItem> listProviderProfileOptions(ProviderProfileOptionQuery query) {
        OptionQuery base = query == null ? EMPTY : query.toBase();
        String kw = keyword(query == null ? null : query.keyword());
        return assemble(base,
                () -> optionsMapper.listProviderProfileOptions(kw),
                vals -> optionsMapper.listProviderProfileOptionsByValues(vals));
    }

    // ==================== 共享逻辑 ====================

    private OptionPage<OptionItem> assemble(
            OptionQuery base,
            java.util.function.Supplier<List<Map<String, Object>>> paged,
            java.util.function.Function<List<String>, List<Map<String, Object>>> byValues) {
        List<String> values = base.values();
        if (values != null && values.size() > OptionQuery.MAX_VALUES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "values 最多 " + OptionQuery.MAX_VALUES + " 项");
        }
        int pn = base.effectivePageNum();
        int ps = base.effectivePageSize();

        List<Map<String, Object>> raw;
        long total;
        try (Page<?> page = PageHelper.startPage(pn, ps)) {
            raw = paged.get();
            total = page.getTotal();
        }

        List<OptionItem> items = toItems(raw);
        List<OptionItem> merged = new ArrayList<>(items);
        if (values != null && !values.isEmpty()) {
            Set<String> present = items.stream()
                    .map(oi -> String.valueOf(oi.value()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (OptionItem oi : toItems(byValues.apply(values))) {
                if (present.add(String.valueOf(oi.value()))) {
                    merged.add(oi);
                }
            }
        }
        return OptionPage.of(merged, total, pn, ps, computeMissing(values, merged));
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

    private static List<OptionItem> toItems(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<OptionItem> out = new ArrayList<>(raw.size());
        for (Map<String, Object> r : raw) {
            out.add(new OptionItem(
                    r.get("value"),
                    (String) r.get("label"),
                    (String) r.get("code"),
                    toBoolean(r.get("disabled")),
                    toMeta(r.get("meta")),
                    (String) r.get("baseUrl")));
        }
        return out;
    }

    /** meta 列由 SQL json_object 生成（JSON 字符串），解析为 Map；空/非法返回 null。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMeta(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            return map.isEmpty() ? null : (Map<String, Object>) map;
        }
        String json = raw.toString().trim();
        if (json.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> parsed = JsonKit.fromJson(json, Map.class);
            return parsed == null || parsed.isEmpty() ? null : parsed;
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean toBoolean(Object d) {
        if (d == null) {
            return null;
        }
        if (d instanceof Boolean b) {
            return b;
        }
        if (d instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = d.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }

    private static List<String> computeMissing(List<String> values, List<OptionItem> merged) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> present = merged.stream()
                .map(oi -> String.valueOf(oi.value()))
                .collect(Collectors.toSet());
        return values.stream().filter(v -> !present.contains(v)).toList();
    }
}
