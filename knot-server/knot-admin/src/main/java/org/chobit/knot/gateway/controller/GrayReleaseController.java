package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.GrayReleaseConverter;
import org.chobit.knot.gateway.dto.grayrelease.GrayPlanDto;
import org.chobit.knot.gateway.service.GrayReleaseService;
import org.chobit.knot.gateway.vo.grayrelease.*;
import jakarta.validation.Valid;
import org.chobit.knot.gateway.vo.grayrelease.GrayPlan;
import org.chobit.knot.gateway.vo.grayrelease.GrayPlanRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/release/gray-plans")
public class GrayReleaseController {
    private final GrayReleaseService grayReleaseService;
    private final GrayReleaseConverter grayReleaseConverter;

    public GrayReleaseController(GrayReleaseService grayReleaseService, GrayReleaseConverter grayReleaseConverter) {
        this.grayReleaseService = grayReleaseService;
        this.grayReleaseConverter = grayReleaseConverter;
    }

    @PostMapping
    public GrayPlan create(@RequestBody @Valid GrayPlanRequest request) {
        GrayPlanDto created = grayReleaseService.create(
                new GrayPlanDto(null, request.targetType(), request.targetId(), request.steps(),
                        request.trafficPercent(), "DRAFT")
        );
        return grayReleaseConverter.toVO(created);
    }

    @PostMapping("/{id}/publish")
    public GrayPlan publish(@PathVariable Long id) {
        GrayPlanDto published = grayReleaseService.publish(id);
        return grayReleaseConverter.toVO(published);
    }

    @PostMapping("/{id}/rollback")
    public GrayPlan rollback(@PathVariable Long id) {
        GrayPlanDto rolledBack = grayReleaseService.rollback(id);
        return grayReleaseConverter.toVO(rolledBack);
    }

    @PostMapping("/list")
    public PageResult<GrayPlan> list(@RequestBody(required = false) PageQuery query) {
        PageResult<GrayPlanDto> page = grayReleaseService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(grayReleaseConverter::toVOList);
    }

}
