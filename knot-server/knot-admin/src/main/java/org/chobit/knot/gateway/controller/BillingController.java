package org.chobit.knot.gateway.controller;

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
import org.chobit.knot.gateway.vo.billing.*;
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

    @PostMapping()
    public BillingRule createRule(@RequestBody @Valid BillingRule request) {
        BillingRuleDto created = billingService.createRule(billingConverter.toRuleDto(request));
        return billingConverter.toRuleVO(created);
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
