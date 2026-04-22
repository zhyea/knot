package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.service.SecurityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security")
public class SecurityController {
    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    @GetMapping("/overview")
    public ApiResponse<SecurityOverview> overview() {
        SecurityService.SecurityOverviewDto overview = securityService.overview();
        return ApiResponse.ok(new SecurityOverview(
                overview.authEnabled(),
                overview.signVerificationEnabled(),
                overview.blockedIpCount(),
                overview.alertCount(),
                overview.cacheHitRate()
        ));
    }

    @PutMapping("/policies")
    public ApiResponse<SecurityPolicy> updatePolicy(@RequestBody SecurityPolicy request) {
        SecurityService.SecurityPolicyDto updated = securityService.updatePolicy(
                new SecurityService.SecurityPolicyDto(request.policyCode(), request.configJson(), request.status())
        );
        return ApiResponse.ok(new SecurityPolicy(updated.policyCode(), updated.configJson(), updated.status()));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<AlertItem>> listAlerts() {
        List<AlertItem> alerts = securityService.listAlerts().stream()
                .map(a -> new AlertItem(a.alertId(), a.level(), a.title(), a.status()))
                .toList();
        return ApiResponse.ok(alerts);
    }

    @PostMapping("/cache/evict")
    public ApiResponse<CacheEvictResult> evictCache(@RequestBody CacheEvictRequest request) {
        SecurityService.CacheEvictResultDto r = securityService.evictCache(request.cacheKey(), request.cacheType());
        return ApiResponse.ok(new CacheEvictResult(r.cacheKey(), r.result()));
    }

    public record SecurityOverview(boolean authEnabled, boolean signVerificationEnabled,
                                   int blockedIpCount, int alertCount, double cacheHitRate) {
    }

    public record SecurityPolicy(String policyCode, String configJson, String status) {
    }

    public record AlertItem(String alertId, String level, String title, String status) {
    }

    public record CacheEvictRequest(String cacheKey, String cacheType) {
    }

    public record CacheEvictResult(String cacheKey, String result) {
    }
}
