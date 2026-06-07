package org.chobit.knot.gateway.auth;

import java.util.Collection;
import java.util.List;

/**
 * 角色相关常量与辅助方法。
 */
public final class AuthRoles {

    public static final String ADMIN = "ADMIN";
    public static final String MASKED_SECRET = "********";

    private AuthRoles() {
    }

    /**
     * Returns whether the role set contains admin.
     */
    public static boolean isAdmin(Collection<String> roles) {
        return roles != null && roles.contains(ADMIN);
    }

    /**
     * Converts the JWT roles claim to a string list.
     */
    @SuppressWarnings("unchecked")
    public static List<String> fromClaim(Object rolesClaim) {
        if (rolesClaim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
