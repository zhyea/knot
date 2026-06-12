package org.chobit.knot.gateway.vo.auth;

import java.util.List;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String realName,
        List<String> roles,
        boolean forcePasswordChange,
        String passwordChangeToken
) {
    public static LoginResponse access(String token,
                                       Long userId,
                                       String username,
                                       String realName,
                                       List<String> roles) {
        return new LoginResponse(token, userId, username, realName, roles, false, null);
    }

    public static LoginResponse forcePasswordChange(Long userId,
                                                    String username,
                                                    String realName,
                                                    String passwordChangeToken) {
        return new LoginResponse(null, userId, username, realName, List.of(), true, passwordChangeToken);
    }
}
