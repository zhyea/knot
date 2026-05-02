package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.converter.AppConverter;
import com.knot.gateway.service.AppService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
public class AppController {
    private final AppService appService;
    private final AppConverter appConverter;

    public AppController(AppService appService, AppConverter appConverter) {
        this.appService = appService;
        this.appConverter = appConverter;
    }

    @GetMapping
    public ApiResponse<PageResult<AppItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<AppService.AppDto> page = appService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(appConverter::toVOList));
    }

    @PostMapping
    public ApiResponse<AppItem> create(@RequestBody @Valid AppItem request) {
        AppService.AppDto created = appService.create(appConverter.toDto(request));
        return ApiResponse.ok(appConverter.toVO(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppItem> update(@PathVariable Long id, @RequestBody @Valid AppItem request) {
        AppService.AppDto updated = appService.update(id, appConverter.toDto(request));
        return ApiResponse.ok(appConverter.toVO(updated));
    }

    @PutMapping("/{id}/quota")
    public ApiResponse<AppItem> updateQuota(@PathVariable Long id, @RequestBody @Valid QuotaPolicy quotaPolicy) {
        AppService.AppDto current = appService.getById(id);
        AppService.AppDto updated = appService.update(id, new AppService.AppDto(
                id, current.appId(), current.name(), current.ownerUserId(), current.enabled(), current.rateLimitPolicy(), quotaPolicy
        ));
        return ApiResponse.ok(appConverter.toVO(updated));
    }

    @GetMapping("/{id}/metrics")
    public ApiResponse<AppMetrics> metrics(@PathVariable Long id) {
        appService.getById(id);
        AppService.AppMetricsDto dto = appService.getAppMetrics(id);
        return ApiResponse.ok(new AppMetrics(dto.appInternalId(), dto.totalRequests(), dto.successRequests(), dto.failedRequests(), dto.tokenUsage()));
    }

    public record AppItem(Long id, String appId, String name, Long owner, boolean enabled,
                          RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
    }

    public record AppMetrics(Long appInternalId, Long totalRequests, Long successRequests, Long failedRequests, Long tokenUsage) {
    }
}
