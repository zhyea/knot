package org.chobit.knot.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.chobit.knot.gateway.service.UserSettingService;
import org.chobit.knot.gateway.vo.user.UserSettingsRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user-settings")
public class UserSettingController {

    private final UserSettingService userSettingService;

    /**
     * Constructs a new instance.
     */
    public UserSettingController(UserSettingService userSettingService) {
        this.userSettingService = userSettingService;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @GetMapping("/me")
    public Map<String, String> listMySettings(HttpServletRequest request) {
        return userSettingService.listSettings(currentUserId(request));
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PutMapping("/me")
    public Map<String, String> saveMySettings(HttpServletRequest request, @RequestBody UserSettingsRequest body) {
        return userSettingService.saveSettings(currentUserId(request), body == null ? null : body.settings());
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
