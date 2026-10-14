package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.auth.CurrentAuth;
import org.chobit.knot.gateway.service.AuthorizationAccessService;
import org.chobit.knot.gateway.vo.auth.AdminAuthorizationInfoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

    private final AuthorizationAccessService authorizationAccessService;
    private final CurrentAuth currentAuth;

    /**
     * Constructs a new instance.
     */
    public CurrentUserController(AuthorizationAccessService authorizationAccessService,
                                 CurrentAuth currentAuth) {
        this.authorizationAccessService = authorizationAccessService;
        this.currentAuth = currentAuth;
    }

    /**
     * Returns the current admin user's authorization information.
     */
    @AuthCheck
    @GetMapping("/authorizations")
    public AdminAuthorizationInfoResponse authorizations() {
        return authorizationAccessService.getAuthorizationInfo(currentAuth.currentUserId());
    }
}
