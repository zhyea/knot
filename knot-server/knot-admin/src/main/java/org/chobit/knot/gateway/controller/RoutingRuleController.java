package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.RoutingRuleConverter;
import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.RoutingRuleService;
import org.chobit.knot.gateway.vo.routing.RoutingRule;
import org.chobit.knot.gateway.vo.routing.RoutingTestRequest;
import org.chobit.knot.gateway.vo.routing.RoutingTestResult;
import jakarta.validation.Valid;
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
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<RoutingRule> list(@RequestBody(required = false) PageQuery query) {
        PageResult<RoutingRuleDto> page = routingRuleService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(routingRuleConverter::toVOList);
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(@RequestParam String code,
                                          @RequestParam(required = false) Long excludeId) {
        return Map.of("available", routingRuleService.isRuleCodeAvailable(code, excludeId));
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "routing", operation = "CREATE", entityType = "RoutingRule",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'鏂板缓璺敱瑙勫垯'",
            recordNewValue = true,
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#result.id())")
    @PostMapping
    /**
     * Creates a new resource.
     */
    public RoutingRule create(@RequestBody @Valid RoutingRule request) {
        RoutingRuleDto created = routingRuleService.create(routingRuleConverter.toDto(request));
        return routingRuleConverter.toVO(created);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingRule",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'鏇存柊璺敱瑙勫垯'",
            recordOldValue = true,
            oldValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    /**
     * Updates the target resource.
     */
    public RoutingRule update(@PathVariable Long id, @RequestBody @Valid RoutingRule request) {
        RoutingRuleDto updated = routingRuleService.update(id, routingRuleConverter.toDto(request));
        return routingRuleConverter.toVO(updated);
    }

    /**
     * Executes a test operation and returns the result. Executes the public operation.
     */
    @PostMapping("/{id}/test")
    public RoutingTestResult test(@PathVariable Long id, @RequestBody @Valid RoutingTestRequest request) {
        return routingRuleService.testInvoke(id, request.secretKey(), request.prompt(), request.model());
    }

}
