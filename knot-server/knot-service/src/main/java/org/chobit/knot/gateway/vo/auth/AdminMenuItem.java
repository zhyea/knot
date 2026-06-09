package org.chobit.knot.gateway.vo.auth;

import java.util.List;

public record AdminMenuItem(
        Long id,
        Long parentId,
        String menuCode,
        String menuName,
        String routePath,
        String componentKey,
        String icon,
        Integer sortOrder,
        List<AdminMenuItem> children
) {
}
