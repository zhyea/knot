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
import org.chobit.knot.gateway.entity.ProviderCredentialEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.mapper.DiscountPolicyMapper;
import org.chobit.knot.gateway.mapper.ProviderCredentialMapper;
import org.chobit.knot.gateway.mapper.ProviderMapper;
import org.chobit.knot.gateway.auth.CurrentAuth;
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
    private final ProviderMapper providerMapper;
    private final ProviderCredentialMapper providerCredentialMapper;
    private final DiscountPolicyMapper discountPolicyMapper;
    private final ProviderConverter providerConverter;
    private final ProviderCredentialSupport credentialSupport;
    private final CurrentAuth currentAuth;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    /**
     * Constructs a new instance.
     */
    public ProviderService(ProviderMapper providerMapper,
                           ProviderCredentialMapper providerCredentialMapper,
                           DiscountPolicyMapper discountPolicyMapper,
                           ProviderConverter providerConverter,
                           ProviderCredentialSupport credentialSupport,
                           CurrentAuth currentAuth,
                           ResourceTrafficPolicySupport trafficPolicySupport) {
        this.providerMapper = providerMapper;
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
    public PageResult<ProviderDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ProviderDto> list(PageRequest pageRequest, String keyword) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ProviderEntity> pageInfo = new PageInfo<>(providerMapper.list(normalizeKeyword(keyword)));
        List<ProviderEntity> entities = pageInfo.getList();
        List<Long> ids = entities.stream().map(ProviderEntity::getId).toList();
        Map<Long, Map<String, Object>> authMap = credentialSupport.loadAuthConfigBatch(ids);
        Map<Long, TrafficPolicies> trafficMap =
                trafficPolicySupport.loadBatch(TrafficResourceTypeEnum.PROVIDER.code(), ids);
        List<ProviderDto> dtos = entities.stream()
                .map(e -> enrich(
                        providerConverter.toDto(e),
                        authMap.get(e.getId()),
                        trafficMap.get(e.getId())))
                .collect(Collectors.toList());
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ProviderDto getById(Long id) {
        ProviderEntity entity = providerMapper.getById(id);
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
        Long count = providerMapper.countByCode(normalized, excludeId);
        return count == null || count == 0;
    }

    /**
     * Returns the audit snapshot used by operation logging.
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
        m.put("authConfig", credentialSupport.maskAuthConfig(loadRawAuthConfig(id)));
        m.put("rateLimitPolicy", dto.rateLimitPolicy());
        m.put("quotaPolicy", dto.quotaPolicy());
        return m;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ProviderDto create(ProviderDto request) {
        String code = resolveCodeForSave(request.code(), null);
        assertCodeAvailable(code, null);
        ProviderEntity entity = providerConverter.toEntity(request);
        entity.setCode(code);
        providerMapper.insert(entity);
        credentialSupport.saveAuthConfig(entity.getId(), resolveAuthConfigForSave(null, request.authConfig()));
        trafficPolicySupport.save(TrafficResourceTypeEnum.PROVIDER.code(), entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ProviderDto update(Long id, ProviderDto request) {
        ProviderEntity existing = providerMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        String code = resolveCodeForSave(request.code(), existing.getCode());
        assertCodeAvailable(code, id);
        ProviderEntity entity = providerConverter.toEntity(request);
        entity.setId(id);
        entity.setCode(code);
        providerMapper.update(entity);
        credentialSupport.saveAuthConfig(id, resolveAuthConfigForSave(id, request.authConfig()));
        trafficPolicySupport.save(TrafficResourceTypeEnum.PROVIDER.code(), id,
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(id);
    }

    private ProviderDto enrich(ProviderEntity entity) {
        ProviderCredentialEntity credential = providerCredentialMapper.getActiveByProviderId(entity.getId());
        TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceTypeEnum.PROVIDER.code(), entity.getId());
        return enrich(
                providerConverter.toDto(entity),
                credentialSupport.toAuthConfig(credential),
                traffic);
    }

    private ProviderDto enrich(ProviderDto base,
                               Map<String, Object> authConfig,
                               TrafficPolicies traffic) {
        Map<String, Object> auth = authConfig != null ? authConfig : ProviderCredentialSupport.defaultAuthConfig();
        if (!currentAuth.isAdmin()) {
            auth = credentialSupport.maskAuthConfig(auth);
        }
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        return new ProviderDto(
                base.id(), base.code(), base.name(), base.type(), base.baseUrl(), base.enabled(),
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

    private static String normalizeCode(String code) {
        return code != null ? code.trim() : "";
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
