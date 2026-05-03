package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.BillingConverter;
import com.knot.gateway.dto.billing.BillingDetailDto;
import com.knot.gateway.dto.billing.BillingRuleDto;
import com.knot.gateway.dto.billing.ReconciliationResultDto;
import com.knot.gateway.service.BillingService;
import com.knot.gateway.vo.billing.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billingService;
    private final BillingConverter billingConverter;

    public BillingController(BillingService billingService, BillingConverter billingConverter) {
        this.billingService = billingService;
        this.billingConverter = billingConverter;
    }

    @GetMapping("/rules")
    public ApiResponse<PageResult<BillingRule>> listRules(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<BillingRuleDto> page = billingService.listRules(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(billingConverter::toRuleVOList));
    }

    @PostMapping("/rules")
    public ApiResponse<BillingRule> createRule(@RequestBody @Valid BillingRule request) {
        BillingRuleDto created = billingService.createRule(billingConverter.toRuleDto(request));
        return ApiResponse.ok(billingConverter.toRuleVO(created));
    }

    @GetMapping("/summary")
    public ApiResponse<BillingSummary> summary() {
        return ApiResponse.ok(billingConverter.toSummaryVO(billingService.summary()));
    }

    @GetMapping("/details")
    public ApiResponse<PageResult<BillingDetail>> details(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<BillingDetailDto> page = billingService.details(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(billingConverter::toDetailVOList));
    }

    @PostMapping("/reconciliation")
    public ApiResponse<ReconciliationResult> reconciliation(@RequestBody @Valid ReconciliationRequest request) {
        ReconciliationResultDto result = billingService.reconcile(request.providerCode(), request.billDate());
        return ApiResponse.ok(billingConverter.toReconciliationVO(result));
    }

}
