package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.entity.ProviderProfileEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.ProviderProfileMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.vo.provider.ProviderProfileItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ProviderProfileService {

    private final ProviderProfileMapper providerProfileMapper;

    public ProviderProfileService(ProviderProfileMapper providerProfileMapper) {
        this.providerProfileMapper = providerProfileMapper;
    }

    public PageResult<ProviderProfileItem> list(String keyword, String tag, PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        List<ProviderProfileItem> rows = providerProfileMapper.list(keyword, tag).stream()
                .map(this::toItem)
                .toList();
        PageInfo<ProviderProfileItem> page = new PageInfo<>(rows);
        return PageResult.of(page.getList(), page.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    public ProviderProfileItem getById(Long id) {
        ProviderProfileEntity entity = providerProfileMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toItem(entity);
    }

    public boolean isCodeAvailable(String code, Long excludeId) {
        return code != null && !code.isBlank()
                && providerProfileMapper.countByCode(code.trim(), excludeId) == 0L;
    }

    @Transactional
    public ProviderProfileItem create(ProviderProfileItem request) {
        validateCode(request.code(), null);
        ProviderProfileEntity entity = toEntity(request);
        providerProfileMapper.insert(entity);
        return getById(entity.getId());
    }

    @Transactional
    public ProviderProfileItem update(Long id, ProviderProfileItem request) {
        getById(id);
        validateCode(request.code(), id);
        ProviderProfileEntity entity = toEntity(request);
        entity.setId(id);
        providerProfileMapper.update(entity);
        return getById(id);
    }

    @Transactional
    public void delete(Long id) {
        getById(id);
        if (providerProfileMapper.countAccountsByProviderId(id) > 0L
                || providerProfileMapper.countCredentialsByProviderId(id) > 0L
                || providerProfileMapper.countDiscountPoliciesByProviderId(id) > 0L
                || providerProfileMapper.countModelsByProviderId(id) > 0L
                || providerProfileMapper.countMappingsByProviderId(id) > 0L
                || providerProfileMapper.countBillingRulesByProviderId(id) > 0L) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        providerProfileMapper.deleteById(id);
    }

    private void validateCode(String code, Long excludeId) {
        if (!isCodeAvailable(code, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private ProviderProfileEntity toEntity(ProviderProfileItem item) {
        ProviderProfileEntity entity = new ProviderProfileEntity();
        entity.setCode(item.code().trim());
        entity.setName(item.name().trim());
        entity.setTag(normalizeTag(item.tag()));
        return entity;
    }

    /**
     * 供应商分类可多选：输入以逗号分隔，这里做 trim、去空、去重，避免存进空项或重复项。
     */
    private String normalizeTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String part : raw.split("[,，]")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                parts.add(value);
            }
        }
        return String.join(",", parts);
    }

    /**
     * 操作日志用的实体快照。
     */
    public Map<String, Object> providerProfileAuditSnapshot(Long id) {
        ProviderProfileEntity entity = providerProfileMapper.getById(id);
        if (entity == null) {
            return Map.of();
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", entity.getId());
        snapshot.put("code", entity.getCode());
        snapshot.put("name", entity.getName());
        snapshot.put("tag", entity.getTag());
        return snapshot;
    }

    private ProviderProfileItem toItem(ProviderProfileEntity entity) {
        return new ProviderProfileItem(entity.getId(), entity.getCode(), entity.getName(), entity.getTag(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
