package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
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

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = userService.login(request.username(), request.password());
        return response;
    }

    @PostMapping("/logout")
    public void logout() {
        // JWT is stateless, client should discard the token
    }
}
