package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.AuthCheck;
import org.chobit.knot.svc.auth.CurrentAuth;
import org.chobit.knot.svc.service.AuthorizationAccessService;
import org.chobit.knot.svc.vo.auth.AdminAuthorizationInfoResponse;
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
