package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ProviderConverter;
import org.chobit.knot.gateway.dto.provider.DiscountPolicyDto;
import org.chobit.knot.gateway.dto.provider.ProviderDto;
import org.chobit.knot.gateway.entity.DiscountPolicyEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.mapper.DiscountPolicyMapper;
import org.chobit.knot.gateway.mapper.ProviderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * 操作审计快照，供 {@link org.chobit.knot.gateway.annotation.OperationLog} SpEL 使用。
     */
    public Map<String, Object> providerAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        ProviderDto dto;
        try {
            dto = getById(id);
        } catch (BusinessException e) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dto.id());
        m.put("code", dto.code());
        m.put("name", dto.name());
        m.put("type", dto.type());
        m.put("baseUrl", dto.baseUrl());
        m.put("enabled", dto.enabled());
        m.put("rateLimitPolicy", dto.rateLimitPolicy());
        m.put("quotaPolicy", dto.quotaPolicy());
        return m;
    }

    @Transactional
    public ProviderDto create(ProviderDto request) {
        ProviderEntity entity = providerConverter.toEntity(request);
        if (entity.getCode() == null || entity.getCode().isBlank()) {
            entity.setCode("provider_" + System.currentTimeMillis());
        }
        providerMapper.insert(entity);
        return providerConverter.toDto(providerMapper.getById(entity.getId()));
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
        return providerConverter.toDto(providerMapper.getById(id));
    }

    // ==================== 折扣策略 ====================

    public List<DiscountPolicyDto> listDiscountPolicies(Long providerId) {
        getById(providerId);
        return discountPolicyMapper.listByProviderId(providerId).stream()
                .map(this::toDiscountPolicyDto)
                .toList();
    }

    @Transactional
    public DiscountPolicyDto createDiscountPolicy(Long providerId, DiscountPolicyDto request) {
        getById(providerId);
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
        getById(providerId);
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

    /**
     * 折扣策略审计快照，供操作日志 SpEL 使用。
     */
    public Map<String, Object> discountPolicyAuditSnapshot(Long policyId) {
        if (policyId == null) {
            return null;
        }
        DiscountPolicyEntity e = discountPolicyMapper.getById(policyId);
        if (e == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("providerId", e.getProviderId());
        m.put("policyName", e.getPolicyName());
        m.put("scopeType", e.getScopeType());
        m.put("scopeRefId", e.getScopeRefId());
        m.put("discountType", e.getDiscountType());
        m.put("discountValue", e.getDiscountValue());
        m.put("priority", e.getPriority());
        m.put("status", e.getStatus());
        return m;
    }

    private DiscountPolicyDto toDiscountPolicyDto(DiscountPolicyEntity e) {
        return new DiscountPolicyDto(
                e.getId(), e.getPolicyName(), e.getScopeType(), e.getScopeRefId(),
                e.getDiscountType(), e.getDiscountValue() != null ? e.getDiscountValue().doubleValue() : 0.0,
                e.getPriority() != null ? e.getPriority() : 100, e.getStatus()
        );
    }
}
