package org.chobit.knot.gateway.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ForcePasswordChangeRequest(
        @NotBlank(message = "改密令牌不能为空") String passwordChangeToken,
        @NotBlank(message = "新密码不能为空") String newPassword
) {
}
