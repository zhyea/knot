package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.dto.provider.ProviderAccountDto;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ProviderConverter;
import org.chobit.knot.gateway.dto.provider.DiscountPolicyDto;
import org.chobit.knot.gateway.entity.DiscountPolicyEntity;
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.chobit.knot.gateway.mapper.DiscountPolicyMapper;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.chobit.knot.gateway.mapper.ProviderAccountMapper;
import org.chobit.knot.gateway.mapper.ProviderProfileMapper;
import org.chobit.knot.gateway.auth.CurrentAuth;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.util.tools.ProviderCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProviderService {
    private final ProviderAccountMapper providerAccountMapper;
    private final ProviderProfileMapper providerProfileMapper;
    private final ProviderCredentialMapper providerCredentialMapper;
    private final DiscountPolicyMapper discountPolicyMapper;
    private final ProviderConverter providerConverter;
    private final ProviderCredentialSupport credentialSupport;
    private final CurrentAuth currentAuth;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    /**
     * Constructs a new instance.
     */
    public ProviderService(ProviderAccountMapper providerAccountMapper,
                           ProviderProfileMapper providerProfileMapper,
                           ProviderCredentialMapper providerCredentialMapper,
                           DiscountPolicyMapper discountPolicyMapper,
                           ProviderConverter providerConverter,
                           ProviderCredentialSupport credentialSupport,
                           CurrentAuth currentAuth,
                           ResourceTrafficPolicySupport trafficPolicySupport) {
        this.providerAccountMapper = providerAccountMapper;
        this.providerProfileMapper = providerProfileMapper;
        this.providerCredentialMapper = providerCredentialMapper;
        this.discountPolicyMapper = discountPolicyMapper;
        this.providerConverter = providerConverter;
        this.credentialSupport = credentialSupport;
        this.currentAuth = currentAuth;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ProviderAccountDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ProviderAccountDto> list(PageRequest pageRequest, String keyword) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ProviderAccountEntity> pageInfo = new PageInfo<>(providerAccountMapper.list(normalizeKeyword(keyword)));
        List<ProviderAccountEntity> entities = pageInfo.getList();
        List<Long> ids = entities.stream().map(ProviderAccountEntity::getId).toList();
        Map<Long, ProviderCredentialEntity> credentialMap = credentialSupport.loadCredentialBatch(ids);
        Map<Long, TrafficPolicies> trafficMap =
                trafficPolicySupport.loadBatch(TrafficResourceTypeEnum.PROVIDER.code(), ids);
        List<ProviderAccountDto> dtos = entities.stream()
                .map(e -> enrich(
                        providerConverter.toDto(e),
                        credentialMap.get(e.getId()),
                        trafficMap.get(e.getId())))
                .collect(Collectors.toList());
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderAccountDto getById(Long id) {
        ProviderAccountEntity entity = providerAccountMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        return enrich(entity);
    }

    /**
     * Returns a suggested value. Executes the public operation.
     */
    public String suggestCode() {
        for (int i = 0; i < 10; i++) {
            String code = ProviderCodes.generate();
            if (isCodeAvailable(code, null)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "无法生成可用供应商编码，请手动填写");
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isCodeAvailable(String code, Long excludeId) {
        String normalized = normalizeCode(code);
        if (normalized.isEmpty()) {
            return false;
        }
        Long count = providerAccountMapper.countByCode(normalized, excludeId);
        return count == null || count == 0;
    }

    /**
     * Returns the audit snapshot used by operation logging.
     */
    public Map<String, Object> providerAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        ProviderAccountDto dto;
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
        m.put("credentialType", dto.credentialType());
        m.put("authConfig", credentialSupport.maskAuthConfig(loadRawAuthConfig(id)));
        m.put("rateLimitPolicy", dto.rateLimitPolicy());
        m.put("quotaPolicy", dto.quotaPolicy());
        return m;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ProviderAccountDto create(ProviderAccountDto request) {
        assertProviderProfileExists(request.providerId());
        ProviderCredentialTypeEnum credentialType = validateCredential(request);
        String code = resolveCodeForSave(request.code(), null);
        assertCodeAvailable(code, null);
        ProviderAccountEntity entity = providerConverter.toEntity(request);
        entity.setCode(code);
        providerAccountMapper.insert(entity);
        credentialSupport.saveAuthConfig(entity.getId(), credentialType.code(),
                resolveAuthConfigForSave(null, request.authConfig()));
        trafficPolicySupport.save(TrafficResourceTypeEnum.PROVIDER.code(), entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ProviderAccountDto update(Long id, ProviderAccountDto request) {
        ProviderAccountEntity existing = providerAccountMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        assertProviderProfileExists(request.providerId());
        ProviderCredentialTypeEnum credentialType = validateCredential(request);
        String code = resolveCodeForSave(request.code(), existing.getCode());
        assertCodeAvailable(code, id);
        ProviderAccountEntity entity = providerConverter.toEntity(request);
        entity.setId(id);
        entity.setCode(code);
        providerAccountMapper.update(entity);
        credentialSupport.saveAuthConfig(id, credentialType.code(),
                resolveAuthConfigForSave(id, request.authConfig()));
        trafficPolicySupport.save(TrafficResourceTypeEnum.PROVIDER.code(), id,
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(id);
    }

    /**
     * Updates the provider enabled status only.
     */
    @Transactional
    public ProviderAccountDto updateStatus(Long id, boolean enabled) {
        ProviderAccountEntity existing = providerAccountMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        providerAccountMapper.updateStatus(id, enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        return getById(id);
    }

    private ProviderAccountDto enrich(ProviderAccountEntity entity) {
        ProviderCredentialEntity credential = providerCredentialMapper.getActiveByProviderId(entity.getId());
        TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceTypeEnum.PROVIDER.code(), entity.getId());
        return enrich(
                providerConverter.toDto(entity),
                credential,
                traffic);
    }

    private ProviderAccountDto enrich(ProviderAccountDto base,
                                      ProviderCredentialEntity credential,
                                      TrafficPolicies traffic) {
        Map<String, Object> auth = credentialSupport.toAuthConfig(credential);
        if (!currentAuth.isAdmin()) {
            auth = credentialSupport.maskAuthConfig(auth);
        }
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        return new ProviderAccountDto(
                base.id(), base.providerId(), base.providerName(),
                base.code(), base.name(), base.type(), base.baseUrl(), base.enabled(),
                base.createdAt(), base.updatedAt(),
                credentialSupport.credentialType(credential),
                auth, rate, quota
        );
    }

    private Map<String, Object> loadRawAuthConfig(Long providerId) {
        ProviderCredentialEntity credential = providerCredentialMapper.getActiveByProviderId(providerId);
        return credentialSupport.toAuthConfig(credential);
    }

    private Map<String, Object> resolveAuthConfigForSave(Long providerId, Map<String, Object> incoming) {
        if (currentAuth.isAdmin()) {
            return incoming;
        }
        if (providerId == null) {
            return incoming;
        }
        return credentialSupport.mergeAuthConfigForSave(incoming, loadRawAuthConfig(providerId));
    }

    private ProviderCredentialTypeEnum validateCredential(ProviderAccountDto request) {
        final ProviderCredentialTypeEnum type;
        try {
            type = ProviderCredentialTypeEnum.fromCode(request.credentialType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的认证类型");
        }
        Map<String, Object> config = request.authConfig();
        for (String field : type.requiredFields()) {
            if (!hasText(config == null ? null : config.get(field))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "认证类型 " + type.code() + " 必须填写字段 " + field);
            }
        }
        return type;
    }

    private static String normalizeCode(String code) {
        return code != null ? code.trim() : "";
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    private static String resolveCodeForSave(String requested, String fallback) {
        String code = normalizeCode(requested);
        if (code.isEmpty()) {
            code = normalizeCode(fallback);
        }
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写供应商编码");
        }
        if (code.length() > ProviderCodes.MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "供应商编码不能超过 " + ProviderCodes.MAX_LENGTH + " 个字符");
        }
        return code;
    }

    private void assertCodeAvailable(String code, Long excludeId) {
        if (!isCodeAvailable(code, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "供应商编码「" + code + "」已存在，请更换后重试");
        }
    }

    private void assertProviderProfileExists(Long providerId) {
        if (providerId == null || providerProfileMapper.getById(providerId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
    }

    // ==================== 折扣策略 ====================

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<DiscountPolicyDto> listDiscountPolicies(Long providerId) {
        getById(providerId);
        return discountPolicyMapper.listByProviderId(providerId).stream()
                .map(this::toDiscountPolicyDto)
                .toList();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
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
        entity.setStatus(request.status() != null ? request.status() : EntityStatusEnum.ACTIVE.code());
        discountPolicyMapper.insert(entity);
        return toDiscountPolicyDto(entity);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
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
     * Executes the public operation. Executes the public operation.
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
