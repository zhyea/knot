package org.chobit.knot.gateway.vo.auth;

import java.util.List;

public record AdminModuleItem(
        Long moduleId,
        String moduleCode,
        String moduleName,
        String icon,
        Integer sortOrder,
        List<AdminMenuItem> menus
) {
}
