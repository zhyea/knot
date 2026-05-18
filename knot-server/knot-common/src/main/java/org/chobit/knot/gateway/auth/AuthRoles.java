package org.chobit.knot.gateway.auth;

import java.util.Collection;
import java.util.List;

/**
 * 角色常量与判断。
 */
public final class AuthRoles {

    public static final String ADMIN = "ADMIN";
    public static final String MASKED_SECRET = "********";

    private AuthRoles() {
    }

    public static boolean isAdmin(Collection<String> roles) {
        return roles != null && roles.contains(ADMIN);
    }

    @SuppressWarnings("unchecked")
    public static List<String> fromClaim(Object rolesClaim) {
        if (rolesClaim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
