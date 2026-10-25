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
 * <p>⚠ 2026-10-20（knot-gateway 切 Netty）：本类此前直接依赖
 * {@code HttpServletRequest} 与 {@code ServletRequestAttributes}，而网关的组件扫描
 * 同样覆盖 {@code org.chobit.knot}——网关没有 Servlet API，一旦实例化就
 * {@code NoClassDefFoundError}，把整个网关启动打断。</p>
 *
 * <p>这里<b>刻意只依赖 {@link RequestAttributes} 接口</b>（它在 {@code spring-web} 里，
 * 不含任何 Servlet 类型），属性读取改用 {@link RequestAttributes#getAttribute(String, int)}
 * 而非强转 {@code ServletRequestAttributes#getRequest()}。于是同一个 bean 在两种栈上都能创建：</p>
 * <ul>
 *   <li>管理端（Spring MVC）：{@code RequestContextHolder} 被 Servlet 容器填充，取值正常；</li>
 *   <li>网关（Netty/WebFlux）：{@code RequestContextHolder} 无人填充，恒返回 {@code null}，
 *       本类退化为「没有当前用户」——网关侧本就不调用它，语义无害。</li>
 * </ul>
 *
 * <p>注意：不能用 {@code @ConditionalOnClass} 把它挡在网关外。它被 {@code ProviderService}
 * 等 service 层的构造器硬依赖，条件装配只会把
 * {@code NoClassDefFoundError} 换成 {@code UnsatisfiedDependencyException}，问题只是换了张脸。</p>
 */
@Component
public class CurrentAuth {

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_USERNAME = "username";
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
     * Returns the current authenticated username.
     */
    public String currentUsername() {
        Object value = currentRequestAttribute(ATTR_USERNAME);
        return value != null ? String.valueOf(value) : null;
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
