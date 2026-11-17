package org.chobit.knot.admin.service.support;

import org.chobit.knot.svc.vo.system.DepartmentItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门扁平列表 → 树：先按 {@code parentId} 分组，再从根（{@code parentId} 为 {@code null}）递归挂载。
 *
 * <p>{@code children} 恒非 {@code null}：叶子节点拿到空列表，前端不必再判空。
 * 父节点不存在的孤儿节点不会被挂到任何地方，也就不会出现在结果里。</p>
 */
public final class DepartmentTreeSupport {

    private DepartmentTreeSupport() {
    }

    /**
     * @param items 扁平列表，顺序即同级节点的展示顺序（由 SQL 的排序决定，本方法不再排序）
     * @return 根节点列表
     */
    public static List<DepartmentItem> build(List<DepartmentItem> items) {
        Map<Long, List<DepartmentItem>> childrenByParent = new LinkedHashMap<>();
        for (DepartmentItem item : items) {
            childrenByParent.computeIfAbsent(item.parentId(), key -> new ArrayList<>()).add(item);
        }
        return buildChildren(null, childrenByParent);
    }

    private static List<DepartmentItem> buildChildren(Long parentId,
                                                     Map<Long, List<DepartmentItem>> childrenByParent) {
        List<DepartmentItem> items = childrenByParent.get(parentId);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<DepartmentItem> result = new ArrayList<>(items.size());
        for (DepartmentItem item : items) {
            result.add(new DepartmentItem(
                    item.id(),
                    item.deptCode(),
                    item.deptName(),
                    item.parentId(),
                    item.status(),
                    item.sortOrder(),
                    item.remark(),
                    item.createdAt(),
                    item.updatedAt(),
                    item.boundCount(),
                    buildChildren(item.id(), childrenByParent)));
        }
        return result;
    }
}
