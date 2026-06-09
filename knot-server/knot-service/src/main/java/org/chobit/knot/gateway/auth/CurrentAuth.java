package org.chobit.knot.gateway.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Component
public class CurrentAuth {

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_USERNAME = "username";
    private static final String ATTR_ROLES = "roles";

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
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
     * Executes the public operation. Executes the public operation.
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
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getAttribute(name);
    }
}
