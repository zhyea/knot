package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.service.AppService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
public class AppController {
    private final AppService appService;

    public AppController(AppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public ApiResponse<List<AppItem>> list() {
        List<AppItem> result = appService.list().stream()
                .map(d -> new AppItem(d.id(), d.appId(), d.name(), String.valueOf(d.ownerUserId()),
                        d.enabled(), d.rateLimitPolicy(), d.quotaPolicy()))
                .toList();
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<AppItem> create(@RequestBody AppItem request) {
        Long ownerId = parseOwnerId(request.owner());
        AppService.AppDto created = appService.create(new AppService.AppDto(
                null, request.appId(), request.name(), ownerId, request.enabled(), request.rateLimitPolicy(), request.quotaPolicy()
        ));
        return ApiResponse.ok(new AppItem(created.id(), created.appId(), created.name(), String.valueOf(created.ownerUserId()),
                created.enabled(), created.rateLimitPolicy(), created.quotaPolicy()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppItem> update(@PathVariable Long id, @RequestBody AppItem request) {
        Long ownerId = parseOwnerId(request.owner());
        AppService.AppDto updated = appService.update(id, new AppService.AppDto(
                id, request.appId(), request.name(), ownerId, request.enabled(), request.rateLimitPolicy(), request.quotaPolicy()
        ));
        return ApiResponse.ok(new AppItem(updated.id(), updated.appId(), updated.name(), String.valueOf(updated.ownerUserId()),
                updated.enabled(), updated.rateLimitPolicy(), updated.quotaPolicy()));
    }

    @PutMapping("/{id}/quota")
    public ApiResponse<AppItem> updateQuota(@PathVariable Long id, @RequestBody QuotaPolicy quotaPolicy) {
        AppService.AppDto current = appService.list().stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "app not found"));
        AppService.AppDto updated = appService.update(id, new AppService.AppDto(
                id, current.appId(), current.name(), current.ownerUserId(), current.enabled(), current.rateLimitPolicy(), quotaPolicy
        ));
        return ApiResponse.ok(new AppItem(updated.id(), updated.appId(), updated.name(), String.valueOf(updated.ownerUserId()),
                updated.enabled(), updated.rateLimitPolicy(), updated.quotaPolicy()));
    }

    @GetMapping("/{id}/metrics")
    public ApiResponse<AppMetrics> metrics(@PathVariable Long id) {
        ensureExists(id);
        return ApiResponse.ok(new AppMetrics(id, 12034L, 11890L, 144L, 923400L));
    }

    private void ensureExists(Long id) {
        boolean exists = appService.list().stream().anyMatch(item -> item.id().equals(id));
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "app not found");
        }
    }

    private Long parseOwnerId(String owner) {
        if (owner == null || owner.isBlank()) return 0L;
        try {
            return Long.parseLong(owner);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public record AppItem(Long id, String appId, String name, String owner, boolean enabled,
                          RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
    }

    public record AppMetrics(Long appInternalId, Long totalRequests, Long successRequests, Long failedRequests, Long tokenUsage) {
    }
}
