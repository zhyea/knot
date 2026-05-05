package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.RoutingRuleConverter;
import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.dto.routing.RoutingSwitchLogDto;
import org.chobit.knot.gateway.service.RoutingRuleService;
import org.chobit.knot.gateway.vo.routing.*;
import jakarta.validation.Valid;
import org.chobit.knot.gateway.vo.routing.RoutingRule;
import org.chobit.knot.gateway.vo.routing.RoutingSwitchLog;
import org.chobit.knot.gateway.vo.routing.RoutingTestRequest;
import org.chobit.knot.gateway.vo.routing.RoutingTestResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {
    private final RoutingRuleService routingRuleService;
    private final RoutingRuleConverter routingRuleConverter;

    public RoutingRuleController(RoutingRuleService routingRuleService, RoutingRuleConverter routingRuleConverter) {
        this.routingRuleService = routingRuleService;
        this.routingRuleConverter = routingRuleConverter;
    }

    @PostMapping("/list")
    public ApiResponse<PageResult<RoutingRule>> list(@RequestBody(required = false) PageQuery query) {
        PageResult<RoutingRuleDto> page = routingRuleService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
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

    @PostMapping("/switch-logs")
    public ApiResponse<PageResult<RoutingSwitchLog>> switchLogs(@RequestBody(required = false) PageQuery query) {
        PageResult<RoutingSwitchLogDto> page = routingRuleService.listSwitchLogs(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return ApiResponse.ok(page.mapList(routingRuleConverter::toSwitchLogVOList));
    }

}
