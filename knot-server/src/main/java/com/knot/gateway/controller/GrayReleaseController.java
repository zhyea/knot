package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.service.GrayReleaseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/release/gray-plans")
public class GrayReleaseController {
    private final GrayReleaseService grayReleaseService;

    public GrayReleaseController(GrayReleaseService grayReleaseService) {
        this.grayReleaseService = grayReleaseService;
    }

    @PostMapping
    public ApiResponse<GrayPlan> create(@RequestBody GrayPlanRequest request) {
        GrayReleaseService.GrayPlanDto created = grayReleaseService.create(
                new GrayReleaseService.GrayPlanDto(null, request.targetType(), request.targetId(), request.steps(),
                        request.trafficPercent(), "DRAFT")
        );
        return ApiResponse.ok(new GrayPlan(created.id(), created.targetType(), created.targetId(),
                created.steps(), created.trafficPercent(), created.status()));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<GrayPlan> publish(@PathVariable Long id) {
        GrayReleaseService.GrayPlanDto published = grayReleaseService.publish(id);
        return ApiResponse.ok(new GrayPlan(published.id(), published.targetType(), published.targetId(),
                published.steps(), published.trafficPercent(), published.status()));
    }

    @PostMapping("/{id}/rollback")
    public ApiResponse<GrayPlan> rollback(@PathVariable Long id) {
        GrayReleaseService.GrayPlanDto rolledBack = grayReleaseService.rollback(id);
        return ApiResponse.ok(new GrayPlan(rolledBack.id(), rolledBack.targetType(), rolledBack.targetId(),
                rolledBack.steps(), rolledBack.trafficPercent(), rolledBack.status()));
    }

    @GetMapping
    public ApiResponse<List<GrayPlan>> list() {
        List<GrayPlan> plans = grayReleaseService.list().stream()
                .map(p -> new GrayPlan(p.id(), p.targetType(), p.targetId(), p.steps(), p.trafficPercent(), p.status()))
                .toList();
        return ApiResponse.ok(plans);
    }

    public record GrayPlanRequest(String targetType, Long targetId, List<Integer> steps, Integer trafficPercent) {
    }

    public record GrayPlan(Long id, String targetType, Long targetId, List<Integer> steps, Integer trafficPercent,
                           String status) {
    }
}
