package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.service.RoutingRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {
    private final RoutingRuleService routingRuleService;

    public RoutingRuleController(RoutingRuleService routingRuleService) {
        this.routingRuleService = routingRuleService;
    }

    @GetMapping
    public ApiResponse<List<RoutingRule>> list() {
        List<RoutingRule> result = routingRuleService.list().stream()
                .map(d -> new RoutingRule(d.id(), d.name(), d.strategy(), d.conditionExpr(), d.targetProviderId(),
                        d.targetModelId(), d.priority(), d.enabled()))
                .toList();
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<RoutingRule> create(@RequestBody RoutingRule request) {
        RoutingRuleService.RoutingRuleDto created = routingRuleService.create(new RoutingRuleService.RoutingRuleDto(
                null, request.name(), request.strategy(), request.conditionExpr(), request.targetProviderId(),
                request.targetModelId(), request.priority(), request.enabled()
        ));
        return ApiResponse.ok(new RoutingRule(created.id(), created.name(), created.strategy(), created.conditionExpr(),
                created.targetProviderId(), created.targetModelId(), created.priority(), created.enabled()));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoutingRule> update(@PathVariable Long id, @RequestBody RoutingRule request) {
        RoutingRuleService.RoutingRuleDto updated = routingRuleService.update(id, new RoutingRuleService.RoutingRuleDto(
                id, request.name(), request.strategy(), request.conditionExpr(), request.targetProviderId(),
                request.targetModelId(), request.priority(), request.enabled()
        ));
        return ApiResponse.ok(new RoutingRule(updated.id(), updated.name(), updated.strategy(), updated.conditionExpr(),
                updated.targetProviderId(), updated.targetModelId(), updated.priority(), updated.enabled()));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<RoutingTestResult> test(@PathVariable Long id, @RequestBody RoutingTestRequest request) {
        ensureExists(id);
        RoutingRule rule = routingRuleService.list().stream().filter(item -> item.id().equals(id))
                .map(d -> new RoutingRule(d.id(), d.name(), d.strategy(), d.conditionExpr(), d.targetProviderId(),
                        d.targetModelId(), d.priority(), d.enabled()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "routing rule not found"));
        return ApiResponse.ok(new RoutingTestResult(rule.id(), rule.targetProviderId(), rule.targetModelId(), "MATCHED"));
    }

    @GetMapping("/switch-logs")
    public ApiResponse<List<RoutingSwitchLog>> switchLogs() {
        List<RoutingSwitchLog> logs = routingRuleService.listSwitchLogs().stream()
                .map(l -> new RoutingSwitchLog(l.reason(), l.fromTarget(), l.toTarget()))
                .toList();
        return ApiResponse.ok(logs);
    }

    private void ensureExists(Long id) {
        boolean exists = routingRuleService.list().stream().anyMatch(item -> item.id().equals(id));
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "routing rule not found");
        }
    }

    public record RoutingRule(Long id, String name, String strategy, String conditionExpr,
                              Long targetProviderId, Long targetModelId, int priority, boolean enabled) {
    }

    public record RoutingTestRequest(String appId, List<String> tags, String time) {
    }

    public record RoutingTestResult(Long matchedRuleId, Long targetProviderId, Long targetModelId, String status) {
    }

    public record RoutingSwitchLog(String reason, String fromTarget, String toTarget) {
    }
}
