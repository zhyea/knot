package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.BillingConverter;
import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.dto.billing.ReconciliationResultDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.BillingService;
import org.chobit.knot.gateway.vo.billing.BillingRule;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.billing.ReconciliationRequest;
import org.chobit.knot.gateway.vo.billing.ReconciliationResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billingService;
    private final BillingConverter billingConverter;

    /**
     * Constructs a new instance.
     */
    public BillingController(BillingService billingService, BillingConverter billingConverter) {
        this.billingService = billingService;
        this.billingConverter = billingConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/rules")
    public PageResult<BillingRule> listRules(@RequestBody(required = false) PageQuery query) {
        PageResult<BillingRuleDto> page = billingService.listRules(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.providerId(),
                query == null ? null : query.logicalModelId()
        );
        return page.mapList(billingConverter::toRuleVOList);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "CREATE", entityType = "BillingRule",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'创建计费规则'",
            recordNewValue = true,
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#result.id())")
    @PostMapping()
    public BillingRule createRule(@RequestBody @Valid BillingRule request) {
        BillingRuleDto created = billingService.createRule(billingConverter.toRuleDto(request));
        return billingConverter.toRuleVO(created);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "UPDATE", entityType = "BillingRule",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新计费规则'",
            recordOldValue = true,
            oldValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)")
    @PutMapping("/rules/{id}")
    public BillingRule updateRule(@PathVariable Long id, @RequestBody @Valid BillingRule request) {
        BillingRuleDto updated = billingService.updateRule(id, billingConverter.toRuleDto(request));
        return billingConverter.toRuleVO(updated);
    }

    /**
     * Updates the target resource status. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "UPDATE", entityType = "BillingRule",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'鏇存柊璁¤垂瑙勫垯鐘舵€?,'",
            recordOldValue = true,
            oldValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)")
    @PutMapping("/rules/{id}/status")
    public BillingRule updateRuleStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        BillingRuleDto updated = billingService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return billingConverter.toRuleVO(updated);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "DELETE", entityType = "BillingRule",
            entityId = "#p0",
            description = "'删除计费规则'",
            recordOldValue = true,
            oldValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)")
    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        billingService.deleteRule(id);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/reconciliation")
    public ReconciliationResult reconciliation(@RequestBody @Valid ReconciliationRequest request) {
        ReconciliationResultDto result = billingService.reconcile(request.providerCode(), request.billDate());
        return billingConverter.toReconciliationVO(result);
    }
}
