package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.converter.AppConverter;
import com.knot.gateway.dto.app.AppDto;
import com.knot.gateway.dto.app.AppMetricsDto;
import com.knot.gateway.service.AppService;
import com.knot.gateway.vo.app.AppItem;
import com.knot.gateway.vo.app.AppMetrics;
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
        PageResult<AppDto> page = appService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(appConverter::toVOList));
    }

    @PostMapping
    public ApiResponse<AppItem> create(@RequestBody @Valid AppItem request) {
        AppDto created = appService.create(appConverter.toDto(request));
        return ApiResponse.ok(appConverter.toVO(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<AppItem> update(@PathVariable Long id, @RequestBody @Valid AppItem request) {
        AppDto updated = appService.update(id, appConverter.toDto(request));
        return ApiResponse.ok(appConverter.toVO(updated));
    }

    @PutMapping("/{id}/quota")
    public ApiResponse<AppItem> updateQuota(@PathVariable Long id, @RequestBody @Valid QuotaPolicy quotaPolicy) {
        AppDto current = appService.getById(id);
        AppDto updated = appService.update(id, new AppDto(
                id, current.appId(), current.name(), current.ownerUserId(), current.enabled(), current.rateLimitPolicy(), quotaPolicy
        ));
        return ApiResponse.ok(appConverter.toVO(updated));
    }

    @GetMapping("/{id}/metrics")
    public ApiResponse<AppMetrics> metrics(@PathVariable Long id) {
        appService.getById(id);
        AppMetricsDto dto = appService.getAppMetrics(id);
        return ApiResponse.ok(new AppMetrics(dto.appInternalId(), dto.totalRequests(), dto.successRequests(), dto.failedRequests(), dto.tokenUsage()));
    }

}
