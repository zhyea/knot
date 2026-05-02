package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.SecurityConverter;
import com.knot.gateway.service.SecurityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security")
public class SecurityController {
    private final SecurityService securityService;
    private final SecurityConverter securityConverter;

    public SecurityController(SecurityService securityService, SecurityConverter securityConverter) {
        this.securityService = securityService;
        this.securityConverter = securityConverter;
    }

    @GetMapping("/overview")
    public ApiResponse<SecurityOverview> overview() {
        return ApiResponse.ok(securityConverter.toOverviewVO(securityService.overview()));
    }

    @PutMapping("/policies")
    public ApiResponse<SecurityPolicy> updatePolicy(@RequestBody @Valid SecurityPolicy request) {
        SecurityService.SecurityPolicyDto updated = securityService.updatePolicy(
                securityConverter.toPolicyDto(request)
        );
        return ApiResponse.ok(securityConverter.toPolicyVO(updated));
    }

    @GetMapping("/alerts")
    public ApiResponse<PageResult<AlertItem>> listAlerts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<SecurityService.AlertItemDto> page = securityService.listAlerts(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(securityConverter::toAlertVOList));
    }

    @PostMapping("/cache/evict")
    public ApiResponse<CacheEvictResult> evictCache(@RequestBody @Valid CacheEvictRequest request) {
        SecurityService.CacheEvictResultDto r = securityService.evictCache(request.cacheKey(), request.cacheType());
        return ApiResponse.ok(securityConverter.toCacheEvictVO(r));
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
