package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.dto.routing.RoutingConsumerDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.RoutingConsumerService;
import org.chobit.knot.gateway.vo.routing.RoutingConsumer;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/routing-consumers")
public class RoutingConsumerController {

    private final RoutingConsumerService routingConsumerService;

    /**
     * Constructs a new instance.
     */
    public RoutingConsumerController(RoutingConsumerService routingConsumerService) {
        this.routingConsumerService = routingConsumerService;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<RoutingConsumer> list(@RequestBody(required = false) PageQuery query) {
        PageResult<RoutingConsumerDto> page =
                routingConsumerService.list(
                        query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                        query == null ? null : query.keyword()
                );
        return page.map(this::toVO);
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(@RequestParam String code,
                                          @RequestParam(required = false) Long excludeId) {
        return Map.of("available", routingConsumerService.isConsumerCodeAvailable(code, excludeId));
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "routing", operation = "CREATE", entityType = "RoutingConsumer",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'鏂板缓娑堣垂鑰?",
            recordNewValue = true,
            newValueSpel = "@routingConsumerService.consumerAuditSnapshot(#result.id())")
    @PostMapping
    /**
     * Creates a new resource.
     */
    public RoutingConsumer create(@RequestBody @Valid RoutingConsumer request) {
        return toVO(routingConsumerService.create(toDto(request)));
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingConsumer",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'鏇存柊娑堣垂鑰?",
            recordOldValue = true,
            oldValueSpel = "@routingConsumerService.consumerAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@routingConsumerService.consumerAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    /**
     * Updates the target resource.
     */
    public RoutingConsumer update(@PathVariable Long id, @RequestBody @Valid RoutingConsumer request) {
        return toVO(routingConsumerService.update(id, toDto(request)));
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingConsumer",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'閲嶇疆娑堣垂鑰?API Key'",
            recordNewValue = true,
            newValueSpel = "@routingConsumerService.consumerAuditSnapshot(#p0)")
    @PostMapping("/{id}/rotate-secret")
    /**
     * Rotates the target secret.
     */
    public RoutingConsumer rotateSecret(@PathVariable Long id) {
        return toVO(routingConsumerService.rotateSecretKey(id));
    }

    private RoutingConsumer toVO(RoutingConsumerDto dto) {
        return new RoutingConsumer(
                dto.id(), dto.consumerCode(), dto.name(), dto.userId(), dto.userName(),
                dto.secretKey(), dto.returnUsageDetail(), dto.enabled(), dto.ruleCount(),
                dto.rateLimitPolicy(), dto.quotaPolicy()
        );
    }

    private RoutingConsumerDto toDto(RoutingConsumer vo) {
        return new RoutingConsumerDto(
                vo.id(), vo.consumerCode(), vo.name(), vo.userId(), vo.userName(),
                vo.secretKey(), vo.returnUsageDetail(), vo.enabled(), vo.ruleCount(),
                vo.rateLimitPolicy(), vo.quotaPolicy()
        );
    }
}
