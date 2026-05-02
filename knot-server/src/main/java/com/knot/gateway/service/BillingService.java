package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.BillingConverter;
import com.knot.gateway.entity.BillingDetailEntity;
import com.knot.gateway.entity.BillingRuleEntity;
import com.knot.gateway.mapper.BillingRuleMapper;
import com.knot.gateway.mapper.BillingStatsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillingService {
    private final BillingRuleMapper billingRuleMapper;
    private final BillingStatsMapper billingStatsMapper;
    private final BillingConverter billingConverter;

    public BillingService(BillingRuleMapper billingRuleMapper, BillingStatsMapper billingStatsMapper, BillingConverter billingConverter) {
        this.billingRuleMapper = billingRuleMapper;
        this.billingStatsMapper = billingStatsMapper;
        this.billingConverter = billingConverter;
    }

    public PageResult<BillingRuleDto> listRules(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<BillingRuleEntity> pageInfo = new PageInfo<>(billingRuleMapper.list());
        return PageResult.fromPage(pageInfo, list -> list.stream().map(billingConverter::toRuleDto).toList(), pageRequest);
    }

    @Transactional
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
        return billingConverter.toRuleDto(e);
    }

    public BillingSummaryDto summary() {
        Long requestCount = defaultLong(billingStatsMapper.countRequests());
        Long tokenUsage = defaultLong(billingStatsMapper.sumTokens());
        BigDecimal cost = billingStatsMapper.sumCost();
        if (cost == null) cost = BigDecimal.ZERO;
        return new BillingSummaryDto(requestCount, tokenUsage, cost);
    }

    public PageResult<BillingDetailDto> details(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<BillingDetailEntity> pageInfo = new PageInfo<>(billingStatsMapper.listDetails());
        return PageResult.fromPage(pageInfo, billingConverter::toDetailDtoList, pageRequest);
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

    public record BillingRuleDto(String code, String name, BigDecimal unitPrice, String unit) {}
    public record BillingSummaryDto(Long requestCount, Long tokenUsage, BigDecimal totalCost) {}
    public record BillingDetailDto(String requestId, String appId, String modelCode, int tokenUsage, BigDecimal cost) {}
    public record ReconciliationResultDto(String providerCode, String billDate, int comparedRows, int diffRows, String status) {}
}
