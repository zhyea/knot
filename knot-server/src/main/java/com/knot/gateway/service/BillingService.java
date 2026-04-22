package com.knot.gateway.service;

import com.knot.gateway.entity.BillingRuleEntity;
import com.knot.gateway.mapper.BillingRuleMapper;
import com.knot.gateway.mapper.BillingStatsMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillingService {
    private final BillingRuleMapper billingRuleMapper;
    private final BillingStatsMapper billingStatsMapper;

    public BillingService(BillingRuleMapper billingRuleMapper, BillingStatsMapper billingStatsMapper) {
        this.billingRuleMapper = billingRuleMapper;
        this.billingStatsMapper = billingStatsMapper;
    }

    public List<BillingRuleDto> listRules() {
        return billingRuleMapper.list().stream().map(this::toDto).toList();
    }

    public BillingRuleDto createRule(BillingRuleDto request) {
        BillingRuleEntity e = new BillingRuleEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setBillingMode("FIXED");
        e.setUnit(request.unit());
        e.setUnitPrice(request.unitPrice());
        e.setStatus("ACTIVE");
        e.setEffectiveFrom(LocalDateTime.now());
        billingRuleMapper.insert(e);
        return toDto(e);
    }

    public BillingSummaryDto summary() {
        Long requestCount = defaultLong(billingStatsMapper.countRequests());
        Long tokenUsage = defaultLong(billingStatsMapper.sumTokens());
        BigDecimal cost = billingStatsMapper.sumCost();
        if (cost == null) cost = BigDecimal.ZERO;
        return new BillingSummaryDto(requestCount, tokenUsage, cost);
    }

    public List<BillingDetailDto> details() {
        return billingStatsMapper.listDetails().stream()
                .map(d -> new BillingDetailDto(d.getRequestId(), String.valueOf(d.getAppId()),
                        String.valueOf(d.getModelId()), d.getTotalTokens(), d.getCostAmount()))
                .toList();
    }

    public ReconciliationResultDto reconcile(String providerCode, String billDate) {
        Integer compared = billingStatsMapper.countStatementsByProviderCode(providerCode);
        if (compared == null) compared = 0;
        int diffRows = 0;
        return new ReconciliationResultDto(providerCode, billDate, compared, diffRows, "DONE");
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private BillingRuleDto toDto(BillingRuleEntity e) {
        return new BillingRuleDto(e.getCode(), e.getName(), e.getUnitPrice(), e.getUnit());
    }

    public record BillingRuleDto(String code, String name, BigDecimal unitPrice, String unit) {}
    public record BillingSummaryDto(Long requestCount, Long tokenUsage, BigDecimal totalCost) {}
    public record BillingDetailDto(String requestId, String appId, String modelCode, int tokenUsage, BigDecimal cost) {}
    public record ReconciliationResultDto(String providerCode, String billDate, int comparedRows, int diffRows, String status) {}
}
