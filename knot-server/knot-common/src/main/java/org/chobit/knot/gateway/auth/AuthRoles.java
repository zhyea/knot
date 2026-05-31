package org.chobit.knot.gateway.auth;

import java.util.Collection;
import java.util.List;

/**
 * Executes the public operation. Executes the public operation.
 */
/**
 * 瑙掕壊甯搁噺涓庡垽鏂€?
 */
public final class AuthRoles {

    public static final String ADMIN = "ADMIN";
    public static final String MASKED_SECRET = "********";

    private AuthRoles() {
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public static boolean isAdmin(Collection<String> roles) {
        return roles != null && roles.contains(ADMIN);
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    @SuppressWarnings("unchecked")
    public static List<String> fromClaim(Object rolesClaim) {
        if (rolesClaim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
