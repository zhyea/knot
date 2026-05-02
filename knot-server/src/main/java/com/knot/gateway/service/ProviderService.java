package com.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.converter.ProviderConverter;
import com.knot.gateway.entity.DiscountPolicyEntity;
import com.knot.gateway.entity.ProviderEntity;
import com.knot.gateway.mapper.DiscountPolicyMapper;
import com.knot.gateway.mapper.ProviderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProviderService {
    private final ProviderMapper providerMapper;
    private final DiscountPolicyMapper discountPolicyMapper;
    private final ProviderConverter providerConverter;

    public ProviderService(ProviderMapper providerMapper, DiscountPolicyMapper discountPolicyMapper, ProviderConverter providerConverter) {
        this.providerMapper = providerMapper;
        this.discountPolicyMapper = discountPolicyMapper;
        this.providerConverter = providerConverter;
    }

    public PageResult<ProviderDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ProviderEntity> pageInfo = new PageInfo<>(providerMapper.list());
        return PageResult.fromPage(pageInfo, providerConverter::toDtoList, pageRequest);
    }


    public ProviderDto getById(Long id) {
        ProviderEntity entity = providerMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provider not found");
        }
        return providerConverter.toDto(entity);
    }

    @Transactional
    public ProviderDto create(ProviderDto request) {
        ProviderEntity entity = providerConverter.toEntity(request);
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            entity.setCode("provider_" + System.currentTimeMillis());
        }
        providerMapper.insert(entity);
        return providerConverter.toDto(entity);
    }

    @Transactional
    public ProviderDto update(Long id, ProviderDto request) {
        ProviderEntity existing = providerMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provider not found");
        }
        ProviderEntity entity = providerConverter.toEntity(request);
        entity.setId(id);
        entity.setCode(existing.getCode());
        providerMapper.update(entity);
        return providerConverter.toDto(entity);
    }

    // ==================== 折扣策略 ====================

    public List<DiscountPolicyDto> listDiscountPolicies(Long providerId) {
        getById(providerId); // ensure exists
        return discountPolicyMapper.listByProviderId(providerId).stream()
                .map(this::toDiscountPolicyDto)
                .toList();
    }

    @Transactional
    public DiscountPolicyDto createDiscountPolicy(Long providerId, DiscountPolicyDto request) {
        getById(providerId); // ensure exists
        DiscountPolicyEntity entity = new DiscountPolicyEntity();
        entity.setProviderId(providerId);
        entity.setPolicyName(request.policyName());
        entity.setScopeType(request.scopeType());
        entity.setScopeRefId(request.scopeRefId());
        entity.setDiscountType(request.discountType());
        entity.setDiscountValue(BigDecimal.valueOf(request.discountValue()));
        entity.setPriority(request.priority());
        entity.setEffectiveFrom(LocalDateTime.now());
        entity.setStatus(request.status() != null ? request.status() : "ACTIVE");
        discountPolicyMapper.insert(entity);
        return toDiscountPolicyDto(entity);
    }

    @Transactional
    public DiscountPolicyDto updateDiscountPolicy(Long providerId, Long policyId, DiscountPolicyDto request) {
        getById(providerId); // ensure exists
        DiscountPolicyEntity entity = discountPolicyMapper.getById(policyId);
        if (entity == null || !entity.getProviderId().equals(providerId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "discount policy not found");
        }
        entity.setPolicyName(request.policyName());
        entity.setScopeType(request.scopeType());
        entity.setScopeRefId(request.scopeRefId());
        entity.setDiscountType(request.discountType());
        entity.setDiscountValue(BigDecimal.valueOf(request.discountValue()));
        entity.setPriority(request.priority());
        entity.setStatus(request.status());
        discountPolicyMapper.update(entity);
        return toDiscountPolicyDto(entity);
    }

    private DiscountPolicyDto toDiscountPolicyDto(DiscountPolicyEntity e) {
        return new DiscountPolicyDto(
                e.getId(), e.getPolicyName(), e.getScopeType(), e.getScopeRefId(),
                e.getDiscountType(), e.getDiscountValue() != null ? e.getDiscountValue().doubleValue() : 0.0,
                e.getPriority() != null ? e.getPriority() : 100, e.getStatus()
        );
    }

    public record ProviderDto(Long id, String code, String name, String type, String baseUrl, boolean enabled,
                              RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {}

    public record DiscountPolicyDto(Long id, String policyName, String scopeType, Long scopeRefId,
                                    String discountType, double discountValue, int priority, String status) {}
}
