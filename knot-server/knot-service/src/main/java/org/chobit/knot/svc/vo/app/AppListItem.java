package org.chobit.knot.svc.vo.app;

/**
 * 应用列表视图：只承载 {@code AppListPanel} 渲染所需的列与定位标识。
 * 限额 / 频控策略与绑定引用数只在详情端点下发，不随列表返回（铁律 12）。
 */
public record AppListItem(
        Long id,
        String appCode,
        String name,
        String deptName,
        String ownerName,
        String remark
) {
}
