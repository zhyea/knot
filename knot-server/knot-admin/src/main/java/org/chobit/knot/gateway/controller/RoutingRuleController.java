package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.RoutingRuleConverter;
import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.RoutingRuleService;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.routing.RoutingRule;
import org.chobit.knot.gateway.vo.routing.RoutingTestRequest;
import org.chobit.knot.gateway.vo.routing.RoutingTestResult;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {
    private final RoutingRuleService routingRuleService;
    private final RoutingRuleConverter routingRuleConverter;

    /**
     * Constructs a new instance.
     */
    public RoutingRuleController(RoutingRuleService routingRuleService, RoutingRuleConverter routingRuleConverter) {
        this.routingRuleService = routingRuleService;
        this.routingRuleConverter = routingRuleConverter;
    }

    /**
     * Lists routing rules with pagination.
     */
    @PostMapping("/list")
    public PageResult<RoutingRule> list(@RequestBody(required = false) PageQuery query) {
        PageResult<RoutingRuleDto> page = routingRuleService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.modelTypes()
        );
        return page.mapList(routingRuleConverter::toVOList);
    }

    /**
     * Checks whether the rule code is available.
     */
    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(@RequestParam String code,
                                          @RequestParam(required = false) Long excludeId) {
        return Map.of("available", routingRuleService.isRuleCodeAvailable(code, excludeId));
    }

    /**
     * Creates a routing rule.
     */
    @OperationLog(module = "routing", operation = "CREATE", entityType = "RoutingRule",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'创建路由规则'",
            recordNewValue = true,
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#result.id())")
    @PostMapping
    public RoutingRule create(@RequestBody @Valid RoutingRule request) {
        RoutingRuleDto created = routingRuleService.create(routingRuleConverter.toDto(request));
        return routingRuleConverter.toVO(created);
    }

    /**
     * Updates a routing rule.
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingRule",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新路由规则'",
            recordOldValue = true,
            oldValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public RoutingRule update(@PathVariable Long id, @RequestBody @Valid RoutingRule request) {
        RoutingRuleDto updated = routingRuleService.update(id, routingRuleConverter.toDto(request));
        return routingRuleConverter.toVO(updated);
    }

    /**
     * Updates the routing rule enabled status.
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingRule",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新路由规则状态'",
            recordOldValue = true,
            oldValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public RoutingRule updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        RoutingRuleDto updated = routingRuleService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return routingRuleConverter.toVO(updated);
    }

    /**
     * Executes routing rule test.
     */
    @PostMapping("/{id}/test")
    public RoutingTestResult test(@PathVariable Long id, @RequestBody @Valid RoutingTestRequest request) {
        return routingRuleService.testInvoke(id,
                request.secretKey(),
                request.prompt(),
                request.model(),
                request.protocol(),
                request.targetType(),
                request.targetId(),
                request.requestBody());
    }
}
