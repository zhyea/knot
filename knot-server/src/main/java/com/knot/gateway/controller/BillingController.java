package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.service.BillingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/rules")
    public ApiResponse<List<BillingRule>> listRules() {
        List<BillingRule> rules = billingService.listRules().stream()
                .map(d -> new BillingRule(d.code(), d.name(), d.unitPrice(), d.unit()))
                .toList();
        return ApiResponse.ok(rules);
    }

    @PostMapping("/rules")
    public ApiResponse<BillingRule> createRule(@RequestBody BillingRule request) {
        BillingService.BillingRuleDto created = billingService.createRule(
                new BillingService.BillingRuleDto(request.code(), request.name(), request.unitPrice(), request.unit())
        );
        return ApiResponse.ok(new BillingRule(created.code(), created.name(), created.unitPrice(), created.unit()));
    }

    @GetMapping("/summary")
    public ApiResponse<BillingSummary> summary() {
        BillingService.BillingSummaryDto summary = billingService.summary();
        return ApiResponse.ok(new BillingSummary(summary.requestCount(), summary.tokenUsage(), summary.totalCost()));
    }

    @GetMapping("/details")
    public ApiResponse<List<BillingDetail>> details() {
        List<BillingDetail> details = billingService.details().stream()
                .map(d -> new BillingDetail(d.requestId(), d.appId(), d.modelCode(), d.tokenUsage(), d.cost()))
                .toList();
        return ApiResponse.ok(details);
    }

    @PostMapping("/reconciliation")
    public ApiResponse<ReconciliationResult> reconciliation(@RequestBody ReconciliationRequest request) {
        BillingService.ReconciliationResultDto result = billingService.reconcile(request.providerCode(), request.billDate());
        return ApiResponse.ok(new ReconciliationResult(
                result.providerCode(),
                result.billDate(),
                result.comparedRows(),
                result.diffRows(),
                result.status()
        ));
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
