package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.dto.auth.ForcePasswordChangeRequest;
import org.chobit.knot.gateway.dto.auth.LoginRequest;
import org.chobit.knot.gateway.service.UserService;
import org.chobit.knot.gateway.vo.auth.LoginResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    /**
     * Constructs a new instance.
     */
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Logs in with username and password.
     */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return userService.login(request.username(), request.password());
    }

    /**
     * Completes the forced password change flow.
     */
    @PostMapping("/force-password-change")
    public void forcePasswordChange(@RequestBody @Valid ForcePasswordChangeRequest request) {
        userService.forcePasswordChange(request.passwordChangeToken(), request.newPassword());
    }

    /**
     * Logs out the current user.
     */
    @PostMapping("/logout")
    public void logout() {
        // JWT is stateless, client should discard the token.
    }
}
