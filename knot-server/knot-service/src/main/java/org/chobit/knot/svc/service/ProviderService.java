package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.auth.UpstreamAuthApplier;
import org.chobit.knot.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.svc.auth.CurrentAuth;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ProviderCredentialTypeEnum;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.svc.converter.ProviderConverter;
import org.chobit.knot.svc.dto.provider.ProviderAccountDto;
import org.chobit.knot.dal.entity.ProviderAccountEntity;
import org.chobit.knot.dal.entity.ProviderCredentialEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.ProviderAccountMapper;
import org.chobit.knot.dal.mapper.ProviderCredentialMapper;
import org.chobit.knot.dal.mapper.ProviderProfileMapper;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.service.support.ProviderCredentialSupport;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.chobit.knot.svc.service.support.AuditSnapshotSupport;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.common.util.tools.ProviderCodes;
import org.chobit.knot.svc.vo.provider.AuthApplierItem;
import org.chobit.knot.svc.vo.provider.CredentialTypeItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ProviderService {
    private final ProviderAccountMapper providerAccountMapper;
    private final ProviderProfileMapper providerProfileMapper;
    private final ProviderCredentialMapper providerCredentialMapper;
    private final ProviderConverter providerConverter;
    private final ProviderCredentialSupport credentialSupport;
    private final CurrentAuth currentAuth;
    private final ResourceTrafficPolicySupport trafficPolicySupport;
    private final UpstreamAuthApplierCatalog authApplierCatalog;

    /**
     * Constructs a new instance.
     */
    public ProviderService(ProviderAccountMapper providerAccountMapper,
                           ProviderProfileMapper providerProfileMapper,
                           ProviderCredentialMapper providerCredentialMapper,
                           ProviderConverter providerConverter,
                           ProviderCredentialSupport credentialSupport,
                           CurrentAuth currentAuth,
                           ResourceTrafficPolicySupport trafficPolicySupport,
                           UpstreamAuthApplierCatalog authApplierCatalog) {
        this.providerAccountMapper = providerAccountMapper;
        this.providerProfileMapper = providerProfileMapper;
        this.providerCredentialMapper = providerCredentialMapper;
        this.providerConverter = providerConverter;
        this.credentialSupport = credentialSupport;
        this.currentAuth = currentAuth;
        this.trafficPolicySupport = trafficPolicySupport;
        this.authApplierCatalog = authApplierCatalog;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    /**
     * 列表只返回列表页展示所需的字段：不读取凭据、不加载频控与额度策略。
     * 认证配置与策略统一由 {@link #getById(Long)} 提供。
     * enabled 为 null 时不过滤状态；true 只返回 ENABLED，false 只返回 DISABLED。
     */
    public PageResult<ProviderAccountDto> list(PageRequest pageRequest, String keyword, Boolean enabled) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ProviderAccountEntity> pageInfo = new PageInfo<>(
                    providerAccountMapper.list(normalizeKeyword(keyword), toStatus(enabled)));
            List<ProviderAccountDto> dtos = pageInfo.getList().stream()
                    .map(providerConverter::toDto)
                    .collect(Collectors.toList());
            return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    private Integer toStatus(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return EnabledStatusEnum.codeOf(Boolean.TRUE.equals(enabled));
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
    /**
     * Lists credential types supported for provider accounts.
     */
    public List<CredentialTypeItem> listCredentialTypes() {
        return Arrays.stream(ProviderCredentialTypeEnum.values())
                .map(type -> new CredentialTypeItem(type.code(), type.label(), type.requiredFields()))
                .toList();
    }

    /**
     * 鉴权策略选项（auth_applier），由 UpstreamAuthApplierCatalog 单一来源下发。
     */
    public List<AuthApplierItem> listAuthAppliers() {
        return authApplierCatalog.definitions().stream()
                .map(item -> new AuthApplierItem(item.code(), item.label(), item.credentialTypes()))
                .toList();
    }

    /**
     * 校验并归一化鉴权策略 code：空放行（运行时回退默认策略），非空必须是已注册且适用于
     * 该认证类型的策略。
     */
    private String validateAuthApplier(String authApplier, ProviderCredentialTypeEnum credentialType) {
        if (StringUtils.isBlank(authApplier)) {
            return null;
        }
        if (!authApplierCatalog.supports(authApplier, credentialType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的鉴权策略：" + authApplier);
        }
        return StringUtils.trim(authApplier);
    }

    /**
     * 校验账户的上游地址：静态接入策略必须填写 base URL，可由凭据派生端点的策略允许为空。
     *
     * <p>判定依据是策略自己的能力声明
     * {@link UpstreamAuthApplier#requiresBaseUrl()}，
     * 而不是供应商名字——「哪个供应商可以不给地址」属于供应商特例，留在策略里，
     * 服务层不必认识 AWS，也就不会因为新增供应商而改这里。</p>
     *
     * <p>停用的账户放行（与模型侧 {@code if (request.enabled())} 的校验口径一致）：
     * 允许先建停用账户、补全配置后再启用。</p>
     */
    private void validateBaseUrl(ProviderAccountDto request, String authApplierCode) {
        if (!request.enabled()) {
            return;
        }
        String baseUrl = StringUtils.trimToNull(request.baseUrl());
        boolean required = StringUtils.isBlank(authApplierCode)
                || authApplierCatalog.resolve(authApplierCode).requiresBaseUrl();
        if (baseUrl == null) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写上游 Base URL");
            }
            return;
        }
        if (!StringUtils.startsWithAny(baseUrl, "http://", "https://")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "上游 Base URL 必须以 http:// 或 https:// 开头");
        }
    }

    public boolean isCodeAvailable(String code, Long excludeId) {
        String normalized = normalizeCode(code);
        if (normalized.isEmpty()) {
            return false;
        }
        Long count = providerAccountMapper.countByCode(normalized, excludeId);
        return count == null || count == 0;
    }

    /**
     * 操作日志用的供应商账户快照：{@code @OperationLog} 的 SpEL 在方法前后各取一次，
     * 账户不存在时返回 {@code null}。凭证原文脱敏后才落日志。
     */
    public Map<String, Object> providerAuditSnapshot(Long id) {
        return AuditSnapshotSupport.capture(id, key -> {
            Map<String, Object> snapshot = JsonKit.toMap(getById(key));
            snapshot.put("authConfig", credentialSupport.maskAuthConfig(loadRawAuthConfig(key)));
            return snapshot;
        });
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ProviderAccountDto create(ProviderAccountDto request) {
        assertProviderProfileCodeExists(request.providerCode());
        ProviderCredentialTypeEnum credentialType = validateCredential(request);
        String authApplier = validateAuthApplier(request.authApplier(), credentialType);
        validateBaseUrl(request, authApplier);
        String code = resolveCodeForSave(request.code(), null);
        assertCodeAvailable(code, null);
        ProviderAccountEntity entity = providerConverter.toEntity(request);
        entity.setCode(code);
        providerAccountMapper.insert(entity);
        credentialSupport.saveAuthConfig(entity.getId(), credentialType.code(),
                authApplier,
                resolveAuthConfigForSave(null, request.authConfig()));
        trafficPolicySupport.save(TrafficResourceTypeEnum.PROVIDER.code(), entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicies());
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
        assertProviderProfileCodeExists(request.providerCode());
        ProviderCredentialTypeEnum credentialType = validateCredential(request);
        String authApplier = validateAuthApplier(request.authApplier(), credentialType);
        validateBaseUrl(request, authApplier);
        // 账户编码被供应商模型按 provider_account_code 引用后不可改：改了会让存量模型掉绑账户
        CodeBindingSupport.requireCodeChangeAllowed(
                existing.getCode(), request.code(), boundCount(existing.getCode()), "供应商账户编码");
        String code = resolveCodeForSave(request.code(), existing.getCode());
        assertCodeAvailable(code, id);
        ProviderAccountEntity entity = providerConverter.toEntity(request);
        entity.setId(id);
        entity.setCode(code);
        providerAccountMapper.update(entity);
        credentialSupport.saveAuthConfig(id, credentialType.code(),
                authApplier,
                resolveAuthConfigForSave(id, request.authConfig()));
        trafficPolicySupport.save(TrafficResourceTypeEnum.PROVIDER.code(), id,
                request.rateLimitPolicy(), request.quotaPolicies());
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
        providerAccountMapper.updateStatus(id, EnabledStatusEnum.codeOf(enabled));
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

    /**
     * 供应商账户编码的绑定处数，两类引用方：
     * <ol>
     *   <li>账户下挂载的供应商模型（kb_models.provider_account_code），逻辑删除可恢复故不按 is_deleted 过滤；</li>
     *   <li>频控/额度绑定行（resource_type='PROVIDER' 指向的就是账户 code，不是供应商主表）——
     *       改码后多态 join 落空，限流与额度会静默失效。</li>
     * </ol>
     */
    private Long boundCount(String accountCode) {
        Long count = providerAccountMapper.countModelsByAccountCode(accountCode);
        long models = count == null ? 0L : count;
        return models + trafficPolicySupport.countBindings(TrafficResourceTypeEnum.PROVIDER.code(), accountCode);
    }

    private ProviderAccountDto enrich(ProviderAccountDto base,
                                      ProviderCredentialEntity credential,
                                      TrafficPolicies traffic) {
        Map<String, Object> auth = credentialSupport.toAuthConfig(credential);
        if (!currentAuth.isAdmin()) {
            auth = credentialSupport.maskAuthConfig(auth);
        }
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        List<QuotaPolicy> quotas = traffic != null ? traffic.quotaPolicies() : List.of();
        return new ProviderAccountDto(
                base.id(), base.providerCode(), base.providerName(),
                base.code(), base.type(), base.baseUrl(), base.enabled(),
                base.createdAt(), base.updatedAt(),
                credentialSupport.credentialType(credential),
                credentialSupport.authApplier(credential),
                auth, rate, quotas, boundCount(base.code())
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
        for (String field : type.requiredFieldNames()) {
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

    private void assertProviderProfileCodeExists(String providerCode) {
        if (providerCode == null || providerProfileMapper.getByCode(providerCode) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
    }

}
