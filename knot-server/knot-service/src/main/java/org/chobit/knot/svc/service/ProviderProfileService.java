package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.dal.entity.ProviderProfileEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.ProviderProfileMapper;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.service.support.AuditSnapshotSupport;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.vo.provider.ProviderProfileItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            List<ProviderProfileItem> rows = providerProfileMapper.list(StringUtils.trimToNull(keyword), StringUtils.trimToNull(tag)).stream()
                    .map(this::toItem)
                    .toList();
            PageInfo<ProviderProfileItem> page = new PageInfo<>(rows);
            return PageResult.of(page.getList(), page.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
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
        ProviderProfileItem existing = getById(id);
        // 供应商编码被账户按 provider_code 引用后不可改：改了会让存量账户掉绑供应商
        CodeBindingSupport.requireCodeChangeAllowed(
                existing.code(), request.code(), existing.boundCount(), "供应商编码");
        validateCode(request.code(), id);
        ProviderProfileEntity entity = toEntity(request);
        entity.setId(id);
        providerProfileMapper.update(entity);
        return getById(id);
    }

    /**
     * 删除供应商档案：档案下只要还挂着账户，或账户下仍有凭据 / 折扣策略 / 模型 / 映射，就拒绝删除。
     *
     * <p>引用检查一律按「档案 code → 账户 → 账户下的资源」这条链查（跨模块绑定存 code 不存 id）。
     * 计费规则不按供应商关联，不在引用检查范围内。</p>
     */
    @Transactional
    public void delete(Long id) {
        ProviderProfileEntity existing = providerProfileMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String providerCode = existing.getCode();
        if (providerProfileMapper.countAccountsByProviderCode(providerCode) > 0L
                || providerProfileMapper.countCredentialsByProviderCode(providerCode) > 0L
                || providerProfileMapper.countDiscountPoliciesByProviderCode(providerCode) > 0L
                || providerProfileMapper.countModelsByProviderCode(providerCode) > 0L
                || providerProfileMapper.countMappingsByProviderCode(providerCode) > 0L) {
            throw new BusinessException(ErrorCode.CONFLICT, "供应商档案下仍有关联账户或账户数据，无法删除");
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
        entity.setRemark(normalizeRemark(item.remark()));
        return entity;
    }

    /**
     * 备注归一化：去首尾空白，纯空白按"无备注"存 NULL，避免库里堆积空串。
     */
    private String normalizeRemark(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
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
     * 操作日志用的供应商档案快照：档案不存在时返回空 map（该域既有口径，保持不变）。
     */
    public Map<String, Object> providerProfileAuditSnapshot(Long id) {
        return AuditSnapshotSupport.capture(id, key -> {
            ProviderProfileEntity entity = providerProfileMapper.getById(key);
            return entity == null ? Map.of() : JsonKit.toMap(entity);
        });
    }

    private ProviderProfileItem toItem(ProviderProfileEntity entity) {
        return new ProviderProfileItem(entity.getId(), entity.getCode(), entity.getName(), entity.getTag(),
                entity.getRemark(), boundCount(entity.getCode()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /**
     * 供应商编码的绑定数（下属供应商账户数）：kb_provider_accounts 按 provider_code 引用本编码。
     */
    private Long boundCount(String providerCode) {
        Long count = providerProfileMapper.countAccountsByProviderCode(providerCode);
        return count == null ? 0L : count;
    }
}
