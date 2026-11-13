package org.chobit.knot.svc.auth;

import org.chobit.knot.common.auth.AuthRoles;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;

/**
 * 当前登录用户（管理端）：从当前请求作用域属性里取鉴权结果
 * （由管理端鉴权拦截器写入 {@code userId} / {@code username} / {@code roles}）。
 *
 * 只依赖 {@link RequestAttributes} 接口；网关侧无人填充时返回 {@code null}，即「没有当前用户」。
 */
@Component
public class CurrentAuth {

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_ROLES = "roles";

    /**
     * Returns whether the current caller holds the admin role.
     */
    public boolean isAdmin() {
        return AuthRoles.isAdmin(currentRoles());
    }

    /**
     * Returns the current authenticated user id.
     */
    public Long currentUserId() {
        Object value = currentRequestAttribute(ATTR_USER_ID);
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * Returns the roles granted to the current caller; empty when unauthenticated.
     */
    @SuppressWarnings("unchecked")
    public List<String> currentRoles() {
        Object roles = currentRequestAttribute(ATTR_ROLES);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Object currentRequestAttribute(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
    }
}
