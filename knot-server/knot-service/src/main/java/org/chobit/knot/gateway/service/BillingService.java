package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.BillingConverter;
import org.chobit.knot.gateway.dto.billing.BillingDetailDto;
import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.dto.billing.BillingSummaryDto;
import org.chobit.knot.gateway.dto.billing.ReconciliationResultDto;
import org.chobit.knot.gateway.entity.BillingDetailEntity;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.BillingStatsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public BillingRuleDto getRuleById(Long id) {
        BillingRuleEntity entity = billingRuleMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        return billingConverter.toRuleDto(entity);
    }

    public Map<String, Object> billingRuleAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            BillingRuleDto dto = getRuleById(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", dto.id());
            m.put("code", dto.code());
            m.put("name", dto.name());
            m.put("unit", dto.unit());
            m.put("unitPrice", dto.unitPrice());
            m.put("enabled", dto.enabled());
            return m;
        } catch (BusinessException e) {
            return null;
        }
    }

    @Transactional
    public BillingRuleDto createRule(BillingRuleDto request) {
        BillingRuleEntity e = new BillingRuleEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setBillingMode("FIXED");
        e.setUnit(request.unit());
        e.setUnitPrice(request.unitPrice());
        e.setStatus(request.enabled() ? "ACTIVE" : "INACTIVE");
        e.setEffectiveFrom(LocalDateTime.now());
        billingRuleMapper.insert(e);
        return billingConverter.toRuleDto(billingRuleMapper.getById(e.getId()));
    }

    @Transactional
    public BillingRuleDto updateRule(Long id, BillingRuleDto request) {
        BillingRuleEntity existing = billingRuleMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing rule not found");
        }
        BillingRuleEntity entity = billingConverter.toRuleEntity(request);
        entity.setId(id);
        entity.setCode(existing.getCode());
        entity.setBillingMode(existing.getBillingMode());
        entity.setEffectiveFrom(existing.getEffectiveFrom());
        billingRuleMapper.update(entity);
        return billingConverter.toRuleDto(billingRuleMapper.getById(id));
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

}
