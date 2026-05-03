package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.RoutingRuleConverter;
import com.knot.gateway.dto.routing.RoutingRuleDto;
import com.knot.gateway.dto.routing.RoutingSwitchLogDto;
import com.knot.gateway.service.RoutingRuleService;
import com.knot.gateway.vo.routing.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {
    private final RoutingRuleService routingRuleService;
    private final RoutingRuleConverter routingRuleConverter;

    public RoutingRuleController(RoutingRuleService routingRuleService, RoutingRuleConverter routingRuleConverter) {
        this.routingRuleService = routingRuleService;
        this.routingRuleConverter = routingRuleConverter;
    }

    @GetMapping
    public ApiResponse<PageResult<RoutingRule>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<RoutingRuleDto> page = routingRuleService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(routingRuleConverter::toVOList));
    }

    @PostMapping
    public ApiResponse<RoutingRule> create(@RequestBody @Valid RoutingRule request) {
        RoutingRuleDto created = routingRuleService.create(routingRuleConverter.toDto(request));
        return ApiResponse.ok(routingRuleConverter.toVO(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoutingRule> update(@PathVariable Long id, @RequestBody @Valid RoutingRule request) {
        RoutingRuleDto updated = routingRuleService.update(id, routingRuleConverter.toDto(request));
        return ApiResponse.ok(routingRuleConverter.toVO(updated));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<RoutingTestResult> test(@PathVariable Long id, @RequestBody @Valid RoutingTestRequest request) {
        RoutingRuleDto rule = routingRuleService.getById(id);
        return ApiResponse.ok(new RoutingTestResult(rule.id(), rule.targetProviderId(), rule.targetModelId(), "MATCHED"));
    }

    @GetMapping("/switch-logs")
    public ApiResponse<PageResult<RoutingSwitchLog>> switchLogs(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<RoutingSwitchLogDto> page = routingRuleService.listSwitchLogs(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(routingRuleConverter::toSwitchLogVOList));
    }

}
