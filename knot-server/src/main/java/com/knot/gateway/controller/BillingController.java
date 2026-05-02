package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.BillingConverter;
import com.knot.gateway.service.BillingService;
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
        PageResult<BillingService.BillingRuleDto> page = billingService.listRules(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(billingConverter::toRuleVOList));
    }

    @PostMapping("/rules")
    public ApiResponse<BillingRule> createRule(@RequestBody @Valid BillingRule request) {
        BillingService.BillingRuleDto created = billingService.createRule(billingConverter.toRuleDto(request));
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
        PageResult<BillingService.BillingDetailDto> page = billingService.details(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(billingConverter::toDetailVOList));
    }

    @PostMapping("/reconciliation")
    public ApiResponse<ReconciliationResult> reconciliation(@RequestBody @Valid ReconciliationRequest request) {
        BillingService.ReconciliationResultDto result = billingService.reconcile(request.providerCode(), request.billDate());
        return ApiResponse.ok(billingConverter.toReconciliationVO(result));
    }

    public record BillingRule(String code, String name, BigDecimal unitPrice, String unit) {
    }

    public record BillingSummary(Long requestCount, Long tokenUsage, BigDecimal totalCost) {
    }

    public record BillingDetail(String requestId, String appId, String modelCode, int tokenUsage, BigDecimal cost) {
    }

    public record ReconciliationRequest(String providerCode, String billDate) {
    }

    public record ReconciliationResult(String providerCode, String billDate, int comparedRows, int diffRows, String status) {
    }
}
