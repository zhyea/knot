package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.converter.AppConverter;
import org.chobit.knot.gateway.dto.app.AppDto;
import org.chobit.knot.gateway.dto.app.AppMetricsDto;
import org.chobit.knot.gateway.service.AppService;
import org.chobit.knot.gateway.vo.app.AppItem;
import org.chobit.knot.gateway.vo.app.AppMetrics;
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

    @PostMapping("/list")
    public PageResult<AppItem> list(@RequestBody PageQuery query) {
        PageResult<AppDto> page = appService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(appConverter::toVOList);
    }

    @PostMapping
    public AppItem create(@RequestBody @Valid AppItem request) {
        AppDto created = appService.create(appConverter.toDto(request));
        return appConverter.toVO(created);
    }

    @PutMapping("/{id}")
    public AppItem update(@PathVariable Long id, @RequestBody @Valid AppItem request) {
        AppDto updated = appService.update(id, appConverter.toDto(request));
        return appConverter.toVO(updated);
    }

    @PutMapping("/{id}/quota")
    public AppItem updateQuota(@PathVariable Long id, @RequestBody @Valid QuotaPolicy quotaPolicy) {
        AppDto current = appService.getById(id);
        AppDto updated = appService.update(id, new AppDto(
                id, current.appId(), current.name(), current.ownerUserId(), current.enabled(), current.rateLimitPolicy(), quotaPolicy
        ));
        return appConverter.toVO(updated);
    }

    @PostMapping("/{id}/metrics")
    public AppMetrics metrics(@PathVariable Long id) {
        appService.getById(id);
        AppMetricsDto dto = appService.getAppMetrics(id);
        return new AppMetrics(dto.appInternalId(), dto.totalRequests(), dto.successRequests(), dto.failedRequests(), dto.tokenUsage());
    }

}
