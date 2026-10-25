package org.chobit.knot.admin.controller;

import jakarta.validation.Valid;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.svc.dto.auth.ForcePasswordChangeRequest;
import org.chobit.knot.svc.dto.auth.LoginRequest;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.admin.error.TooManyRequestsException;
import org.chobit.knot.admin.security.BruteForceGuard;
import org.chobit.knot.svc.service.UserService;
import org.chobit.knot.svc.vo.auth.LoginResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final BruteForceGuard loginBruteForceGuard;

    /**
     * Constructs a new instance.
     */
    public AuthController(UserService userService,
                         @Qualifier("loginBruteForceGuard") BruteForceGuard loginBruteForceGuard) {
        this.userService = userService;
        this.loginBruteForceGuard = loginBruteForceGuard;
    }

    /**
     * Logs in with username and password.
     */
    @OperationLog(module = "auth", operation = "LOGIN", entityType = "User",
            entityName = "#request.username()",
            description = "'用户登录'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        String username = request.username();
        // 该用户名在窗口内失败次数已达封禁阈值：直接拒绝，抬高密码爆破成本。
        if (loginBruteForceGuard.isBlocked(username)) {
            throw new TooManyRequestsException("登录尝试过于频繁，请稍后再试");
        }
        try {
            LoginResponse response = userService.login(username, request.password());
            // 登录成功清零失败计数，避免累积误封。
            loginBruteForceGuard.reset(username);
            return response;
        } catch (BusinessException e) {
            loginBruteForceGuard.recordFailure(username);
            throw e;
        }
    }

    /**
     * Completes the forced password change flow.
     */
    @OperationLog(module = "auth", operation = "UPDATE", entityType = "User",
            description = "'强制修改密码'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/force-password-change")
    public void forcePasswordChange(@RequestBody @Valid ForcePasswordChangeRequest request) {
        userService.forcePasswordChange(request.passwordChangeToken(), request.newPassword());
    }

    /**
     * Logs out the current user.
     */
    @OperationLog(module = "auth", operation = "LOGOUT", entityType = "User",
            description = "'用户退出登录'",
            recordOldValue = false,
            recordNewValue = false)
    @PostMapping("/logout")
    public void logout() {
        // JWT is stateless, client should discard the token.
    }
}
