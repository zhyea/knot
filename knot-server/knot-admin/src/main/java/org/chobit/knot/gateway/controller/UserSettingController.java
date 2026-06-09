package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.AuthCheck;
import org.chobit.knot.gateway.auth.CurrentAuth;
import org.chobit.knot.gateway.service.UserSettingService;
import org.chobit.knot.gateway.vo.user.UserSettingsRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user-settings")
public class UserSettingController {

    private final UserSettingService userSettingService;
    private final CurrentAuth currentAuth;

    /**
     * Constructs a new instance.
     */
    public UserSettingController(UserSettingService userSettingService,
                                 CurrentAuth currentAuth) {
        this.userSettingService = userSettingService;
        this.currentAuth = currentAuth;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @AuthCheck
    @GetMapping("/me")
    public Map<String, String> listMySettings() {
        return userSettingService.listSettings(currentAuth.currentUserId());
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @AuthCheck
    @PutMapping("/me")
    public Map<String, String> saveMySettings(@RequestBody UserSettingsRequest body) {
        return userSettingService.saveSettings(currentAuth.currentUserId(), body == null ? null : body.settings());
    }
}
