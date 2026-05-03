package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
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
    public ApiResponse<GrayPlan> create(@RequestBody @Valid GrayPlanRequest request) {
        GrayPlanDto created = grayReleaseService.create(
                new GrayPlanDto(null, request.targetType(), request.targetId(), request.steps(),
                        request.trafficPercent(), "DRAFT")
        );
        return ApiResponse.ok(grayReleaseConverter.toVO(created));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<GrayPlan> publish(@PathVariable Long id) {
        GrayPlanDto published = grayReleaseService.publish(id);
        return ApiResponse.ok(grayReleaseConverter.toVO(published));
    }

    @PostMapping("/{id}/rollback")
    public ApiResponse<GrayPlan> rollback(@PathVariable Long id) {
        GrayPlanDto rolledBack = grayReleaseService.rollback(id);
        return ApiResponse.ok(grayReleaseConverter.toVO(rolledBack));
    }

    @GetMapping
    public ApiResponse<PageResult<GrayPlan>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<GrayPlanDto> page = grayReleaseService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(grayReleaseConverter::toVOList));
    }

}
