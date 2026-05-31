package org.chobit.knot.gateway.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Component
public class CurrentAuth {

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isAdmin() {
        return AuthRoles.isAdmin(currentRoles());
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @SuppressWarnings("unchecked")
    public List<String> currentRoles() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return List.of();
        }
        HttpServletRequest request = attrs.getRequest();
        Object roles = request.getAttribute("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
