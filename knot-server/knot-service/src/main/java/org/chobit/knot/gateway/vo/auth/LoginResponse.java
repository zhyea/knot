package org.chobit.knot.gateway.vo.auth;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String realName
) {
}
