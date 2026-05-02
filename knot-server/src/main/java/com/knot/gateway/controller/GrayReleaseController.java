package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.GrayReleaseConverter;
import com.knot.gateway.service.GrayReleaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        GrayReleaseService.GrayPlanDto created = grayReleaseService.create(
                new GrayReleaseService.GrayPlanDto(null, request.targetType(), request.targetId(), request.steps(),
                        request.trafficPercent(), "DRAFT")
        );
        return ApiResponse.ok(grayReleaseConverter.toVO(created));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<GrayPlan> publish(@PathVariable Long id) {
        GrayReleaseService.GrayPlanDto published = grayReleaseService.publish(id);
        return ApiResponse.ok(grayReleaseConverter.toVO(published));
    }

    @PostMapping("/{id}/rollback")
    public ApiResponse<GrayPlan> rollback(@PathVariable Long id) {
        GrayReleaseService.GrayPlanDto rolledBack = grayReleaseService.rollback(id);
        return ApiResponse.ok(grayReleaseConverter.toVO(rolledBack));
    }

    @GetMapping
    public ApiResponse<PageResult<GrayPlan>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<GrayReleaseService.GrayPlanDto> page = grayReleaseService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(grayReleaseConverter::toVOList));
    }

    public record GrayPlanRequest(String targetType, Long targetId, List<Integer> steps, Integer trafficPercent) {
    }

    public record GrayPlan(Long id, String targetType, Long targetId, List<Integer> steps, Integer trafficPercent,
                           String status) {
    }
}
