package org.chobit.knot.gateway.vo.auth;

import java.util.List;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String realName,
        List<String> roles
) {
}
