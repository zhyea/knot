package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.BillingConverter;
import org.chobit.knot.gateway.dto.billing.BillingDetailDto;
import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.dto.billing.ReconciliationResultDto;
import org.chobit.knot.gateway.service.BillingService;
import org.chobit.knot.gateway.vo.billing.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billingService;
    private final BillingConverter billingConverter;

    public BillingController(BillingService billingService, BillingConverter billingConverter) {
        this.billingService = billingService;
        this.billingConverter = billingConverter;
    }

    @PostMapping("/rules")
    public PageResult<BillingRule> listRules(@RequestBody(required = false) PageQuery query) {
        PageResult<BillingRuleDto> page = billingService.listRules(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(billingConverter::toRuleVOList);
    }

    @OperationLog(module = "billing", operation = "CREATE", entityType = "BillingRule",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'新建计费规则'",
            recordNewValue = true,
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#result.id())")
    @PostMapping()
    public BillingRule createRule(@RequestBody @Valid BillingRule request) {
        BillingRuleDto created = billingService.createRule(billingConverter.toRuleDto(request));
        return billingConverter.toRuleVO(created);
    }

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

    @PostMapping("/summary")
    public BillingSummary summary() {
        return billingConverter.toSummaryVO(billingService.summary());
    }

    @PostMapping("/details")
    public PageResult<BillingDetail> details(@RequestBody(required = false) PageQuery query) {
        PageResult<BillingDetailDto> page = billingService.details(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(billingConverter::toDetailVOList);
    }

    @PostMapping("/reconciliation")
    public ReconciliationResult reconciliation(@RequestBody @Valid ReconciliationRequest request) {
        ReconciliationResultDto result = billingService.reconcile(request.providerCode(), request.billDate());
        return billingConverter.toReconciliationVO(result);
    }
}
