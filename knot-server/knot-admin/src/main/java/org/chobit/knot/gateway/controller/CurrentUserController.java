package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.auth.CurrentAuth;
import org.chobit.knot.gateway.service.AdminAuthorizationService;
import org.chobit.knot.gateway.vo.auth.AdminAuthorizationInfoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

    private final AdminAuthorizationService adminAuthorizationService;
    private final CurrentAuth currentAuth;

    /**
     * Constructs a new instance.
     */
    public CurrentUserController(AdminAuthorizationService adminAuthorizationService,
                                 CurrentAuth currentAuth) {
        this.adminAuthorizationService = adminAuthorizationService;
        this.currentAuth = currentAuth;
    }

    /**
     * Returns the current admin user's authorization information.
     */
    @AuthCheck
    @GetMapping("/authorizations")
    public AdminAuthorizationInfoResponse authorizations() {
        return adminAuthorizationService.getAuthorizationInfo(currentAuth.currentUserId());
    }
}
