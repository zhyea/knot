package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.protocol.UpstreamAdapterCatalog;
import org.chobit.knot.common.constants.enums.BillingModeEnum;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ModelTypeEnum;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.svc.converter.ModelConverter;
import org.chobit.knot.svc.dto.model.ModelApiBindingDto;
import org.chobit.knot.svc.dto.model.ModelDto;
import org.chobit.knot.svc.dto.provider.DiscountPolicyDto;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.entity.DiscountPolicyEntity;
import org.chobit.knot.dal.entity.LogicalModelEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ProviderAccountEntity;
import org.chobit.knot.dal.entity.ProviderModelMappingEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.dal.mapper.DiscountPolicyMapper;
import org.chobit.knot.dal.mapper.LogicalModelMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ProviderAccountMapper;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.AuditSnapshotSupport;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.service.support.ModelApiBindingSupport;
import org.chobit.knot.svc.service.support.RequiredTextSupport;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.chobit.knot.adapter.usage.extractor.UsageExtractorCatalog;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.vo.model.ModelApiProtocolItem;
import org.chobit.knot.svc.vo.model.ModelTypeItem;
import org.chobit.knot.svc.vo.model.RequestAdapterItem;
import org.chobit.knot.svc.vo.model.UsageExtractorItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ModelService {

    /** 模型级 Base Path 上限，与 kb_models.base_path 列宽一致。 */
    private static final int BASE_PATH_MAX_LENGTH = 255;

    /**
     * 模型级 Base Path 允许的字符集：ASCII 路径安全字符。
     *
     * <p>刻意不含 {@code %}——Bedrock 的模型 ID 会编码成 {@code %3A} 出现在协议路径里，
     * 若允许用户手写百分号，会与已编码路径混在一起难以区分「谁的编码」。</p>
     */
    private static final Pattern BASE_PATH_PATTERN = Pattern.compile("^/[A-Za-z0-9._~\\-/]*$");

    private final ModelMapper modelMapper;
    private final DiscountPolicyMapper discountPolicyMapper;
    private final ModelApiBindingSupport apiBindingSupport;
    private final LogicalModelMapper logicalModelMapper;
    private final BillingRuleMapper billingRuleMapper;
    private final ProviderAccountMapper providerAccountMapper;
    private final ModelConverter modelConverter;
    private final ResourceTrafficPolicySupport trafficPolicySupport;
    private final UsageExtractorCatalog usageExtractorCatalog;
    private final UpstreamAdapterCatalog requestAdapterCatalog;

    /**
     * Constructs a new instance.
     */
    public ModelService(ModelMapper modelMapper,
                        DiscountPolicyMapper discountPolicyMapper,
                        ModelApiBindingSupport apiBindingSupport,
                        LogicalModelMapper logicalModelMapper,
                        BillingRuleMapper billingRuleMapper,
                        ProviderAccountMapper providerAccountMapper,
                        ModelConverter modelConverter,
                        ResourceTrafficPolicySupport trafficPolicySupport,
                        UsageExtractorCatalog usageExtractorCatalog,
                        UpstreamAdapterCatalog requestAdapterCatalog) {
        this.modelMapper = modelMapper;
        this.discountPolicyMapper = discountPolicyMapper;
        this.apiBindingSupport = apiBindingSupport;
        this.logicalModelMapper = logicalModelMapper;
        this.billingRuleMapper = billingRuleMapper;
        this.providerAccountMapper = providerAccountMapper;
        this.modelConverter = modelConverter;
        this.trafficPolicySupport = trafficPolicySupport;
        this.usageExtractorCatalog = usageExtractorCatalog;
        this.requestAdapterCatalog = requestAdapterCatalog;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ModelDto> list(PageRequest pageRequest) {
        return list(pageRequest, null, null, null, null, null);
    }

    /**
     * Returns matching results. Executes the public operation.
     */
    public PageResult<ModelDto> list(PageRequest pageRequest, String keyword) {
        return list(pageRequest, keyword, null, null, null, null);
    }

    /**
     * Returns matching results. Executes the public operation.
     *
     * @param logicalModelCode 按绑定统一模型过滤（模型池选模型时用），为空不过滤
     * @param status           按状态过滤（路由规则绑定目标时传 ENABLED，只出已启用模型），为空不过滤
     * @param includeDeleted   管理列表传 true 以便展示已删除行（浅红底 + 恢复按钮），为空等同 false
     */
    public PageResult<ModelDto> list(PageRequest pageRequest,
                                     String keyword,
                                     List<String> modelTypes,
                                     String logicalModelCode,
                                     String status,
                                     Boolean includeDeleted) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ModelEntity> pageInfo = new PageInfo<>(
                    modelMapper.list(StringUtils.trimToNull(keyword), normalizeModelTypes(modelTypes),
                            normalizeTextToNull(logicalModelCode), EnabledStatusEnum.parse(status), includeDeleted)
            );
            List<ModelEntity> entities = pageInfo.getList();
            List<Long> ids = entities.stream().map(ModelEntity::getId).toList();
            Map<Long, TrafficPolicies> trafficMap =
                    trafficPolicySupport.loadBatch(TrafficResourceTypeEnum.MODEL.code(), ids);
            Map<Long, List<ModelApiBindingDto>> bindingMap = apiBindingSupport.mapByModelIds(ids);
            List<ModelDto> dtos = entities.stream()
                    .map(entity -> enrich(modelConverter.toDto(entity), trafficMap.get(entity.getId()), bindingMap.get(entity.getId())))
                    .collect(Collectors.toList());
            return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelDto getById(Long id) {
        ModelEntity entity = modelMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商模型不存在");
        }
        return enrich(entity);
    }

    /**
     * 操作日志用的供应商模型快照：{@code @OperationLog} 的 SpEL 在方法前后各取一次，
     * 模型不存在时返回 {@code null}。
     */
    public Map<String, Object> modelAuditSnapshot(Long id) {
        return AuditSnapshotSupport.capture(id, key -> JsonKit.toMap(getById(key)));
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isModelCodeAvailable(String modelCode, Long excludeId) {
        String code = modelCode != null ? modelCode.trim() : "";
        if (code.isEmpty()) {
            return false;
        }
        Long count = modelMapper.countByModelCode(code, excludeId);
        return count == null || count == 0;
    }

    /**
     * Lists available usage extractors.
     */
    public List<UsageExtractorItem> listUsageExtractors() {
        return usageExtractorCatalog.definitions().stream()
                .map(item -> new UsageExtractorItem(
                        item.code(),
                        item.label(),
                        item.description(),
                        item.className(),
                        item.streamSupported(),
                        item.billingModes().stream().map(BillingModeEnum::code).toList()
                ))
                .toList();
    }

    /**
     * Lists available upstream request adapters.
     */
    public List<RequestAdapterItem> listRequestAdapters() {
        return requestAdapterCatalog.definitions().stream()
                .map(item -> new RequestAdapterItem(
                        item.code(),
                        item.label(),
                        item.className(),
                        item.protocol()
                ))
                .toList();
    }

    /**
     * Lists model API protocols maintained by {@link ModelApiProtocolEnum}.
     */
    public List<ModelApiProtocolItem> listApiProtocols() {
        return Arrays.stream(ModelApiProtocolEnum.values())
                .map(protocol -> new ModelApiProtocolItem(
                        protocol.code(),
                        protocol.displayName(),
                        protocol.defaultPath(),
                        protocol.streamSupported(),
                        protocol.canonical().code()
                ))
                .toList();
    }

    /**
     * Lists model types maintained by {@link ModelTypeEnum}, ordered by sort order.
     */
    public List<ModelTypeItem> listModelTypes() {
        return ModelTypeEnum.sorted().stream()
                .map(type -> new ModelTypeItem(
                        type.code(),
                        type.displayName(),
                        type.sortOrder(),
                        type.supportedProtocolCodes()
                ))
                .toList();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ModelDto create(ModelDto request) {
        String modelCode = normalizeModelCode(request.modelCode());
        assertModelCodeAvailable(modelCode, null);
        String modelType = validateModelRequest(request);
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setModelCode(modelCode);
        entity.setUpstreamModel(request.upstreamModel().trim());
        entity.setBasePath(normalizeBasePath(request.basePath()));
        modelMapper.insert(entity);
        trafficPolicySupport.save(
                TrafficResourceTypeEnum.MODEL.code(),
                entity.getId(),
                request.rateLimitPolicy(),
                request.quotaPolicies()
        );
        saveLogicalModelMapping(entity.getId(), request.logicalModelCode(), modelCode);
        apiBindingSupport.save(entity.getId(), modelCode, request.apiBindings(), modelType);
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ModelDto update(Long id, ModelDto request) {
        ModelEntity existing = modelMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
        }
        String modelCode = normalizeModelCode(request.modelCode());
        // 模型编码被路由目标 / 池条目 / 厂商映射 / API 绑定 / 折扣策略引用后不可改
        CodeBindingSupport.requireCodeChangeAllowed(
                existing.getModelCode(), modelCode, boundCount(existing.getModelCode()), "供应商模型编码");
        assertModelCodeAvailable(modelCode, id);
        String modelType = validateModelRequest(request);
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setId(id);
        entity.setModelCode(modelCode);
        entity.setUpstreamModel(request.upstreamModel().trim());
        entity.setBasePath(normalizeBasePath(request.basePath()));
        modelMapper.update(entity);
        trafficPolicySupport.save(
                TrafficResourceTypeEnum.MODEL.code(),
                id,
                request.rateLimitPolicy(),
                request.quotaPolicies()
        );
        saveLogicalModelMapping(id, request.logicalModelCode(), modelCode);
        if (request.apiBindings() != null) {
            apiBindingSupport.save(id, modelCode, request.apiBindings(), modelType);
        }
        return getById(id);
    }

    /**
     * Updates the model enabled status only. Enabling runs full validation; disabling skips it.
     */
    @Transactional
    public ModelDto updateStatus(Long id, boolean enabled) {
        ModelDto existing = getById(id);
        if (enabled) {
            ModelDto request = new ModelDto(
                    existing.id(),
                    existing.modelCode(),
                    existing.upstreamModel(),
                    existing.providerAccountCode(),
                    existing.providerName(),
                    existing.providerCode(),
                    existing.modelType(),
                    existing.version(),
                    existing.basePath(),
                    existing.remark(),
                    enabled,
                    existing.logicalModelCode(),
                    existing.billingRuleCode(),
                    existing.rateLimitPolicy(),
                    existing.quotaPolicies(),
                    existing.apiBindings(),
                    existing.deleted(),
                    existing.boundCount()
            );
            validateModelRequest(request);
        }
        modelMapper.updateStatus(id, EnabledStatusEnum.codeOf(enabled));
        return getById(id);
    }

    /**
     * 逻辑删除供应商模型。被引用时拒绝删除（先解除引用再删）。
     *
     * <p>不走物理删除：历史路由目标的 target_code 仍需能解析出可读信息，且删除可恢复。</p>
     *
     * <p>四类引用都要拦：路由规则目标、模型池条目、厂商模型映射、模型 API 协议绑定。
     * 后三类若不拦，逻辑删除后对应的 inner join 查询会直接落空（池内条目「凭空消失」、
     * API 绑定查不到协议），属于静默数据损坏。</p>
     */
    @Transactional
    public void delete(Long id) {
        ModelEntity existing = modelMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商模型不存在");
        }
        requireNoReference(modelMapper.countRoutingTargetsByModelCode(existing.getModelCode()),
                "该供应商模型已被路由规则引用，无法删除");
        requireNoReference(modelMapper.countPoolItemsByModelCode(existing.getModelCode()),
                "该供应商模型已被模型池引用，无法删除");
        requireNoReference(modelMapper.countProviderMappingsByModelCode(existing.getModelCode()),
                "该供应商模型已被厂商模型映射引用，无法删除");
        requireNoReference(modelMapper.countApiBindingsByModelCode(existing.getModelCode()),
                "该供应商模型已被 API 协议绑定引用，无法删除");
        requireNoReference(discountPolicyMapper.countByModelCode(existing.getModelCode()),
                "该供应商模型已被折扣策略引用，无法删除");
        modelMapper.logicalDelete(id);
    }

    // ==================== 折扣策略（绑定供应商模型 model_code） ====================

    /**
     * 列出某供应商模型下的折扣策略。modelCode 为业务码（kb_models.model_code），非主键 id。
     */
    public List<DiscountPolicyDto> listDiscountPolicies(String modelCode) {
        requireExistingModel(modelCode);
        return discountPolicyMapper.listByModelCode(modelCode).stream()
                .map(this::toDiscountPolicyDto)
                .toList();
    }

    @Transactional
    public DiscountPolicyDto createDiscountPolicy(String modelCode, DiscountPolicyDto request) {
        requireExistingModel(modelCode);
        DiscountPolicyEntity entity = new DiscountPolicyEntity();
        entity.setModelCode(modelCode);
        entity.setDiscountType(request.discountType());
        entity.setDiscountValue(request.discountValue());
        entity.setEffectiveFrom(LocalDateTime.now());
        entity.setStatus(request.status() != null ? request.status() : EnabledStatusEnum.ENABLED.code());
        discountPolicyMapper.insert(entity);
        return toDiscountPolicyDto(entity);
    }

    @Transactional
    public DiscountPolicyDto updateDiscountPolicy(String modelCode, Long policyId, DiscountPolicyDto request) {
        requireExistingModel(modelCode);
        DiscountPolicyEntity entity = discountPolicyMapper.getById(policyId);
        // 归属模型不可跨模型改：命中不到即视为不存在
        if (entity == null || !modelCode.equals(entity.getModelCode())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "折扣策略不存在");
        }
        entity.setDiscountType(request.discountType());
        entity.setDiscountValue(request.discountValue());
        entity.setStatus(request.status());
        discountPolicyMapper.update(entity);
        return toDiscountPolicyDto(entity);
    }

    /**
     * 操作日志用的折扣策略快照：策略不存在时返回 {@code null}。
     */
    public Map<String, Object> discountPolicyAuditSnapshot(Long policyId) {
        return AuditSnapshotSupport.capture(policyId, key -> {
            DiscountPolicyEntity entity = discountPolicyMapper.getById(key);
            return entity == null ? null : JsonKit.toMap(entity);
        });
    }

    /** 折扣策略按模型业务码绑定，模型必须存在且未删除。 */
    private void requireExistingModel(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型业务码不能为空");
        }
        ModelEntity existing = modelMapper.getByCode(modelCode);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商模型不存在");
        }
    }

    private DiscountPolicyDto toDiscountPolicyDto(DiscountPolicyEntity e) {
        return new DiscountPolicyDto(
                e.getId(),
                e.getDiscountType(), e.getDiscountValue(),
                e.getStatus()
        );
    }

    private static void requireNoReference(Long count, String message) {
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    /**
     * 恢复已逻辑删除的供应商模型。
     *
     * <p>model_code 唯一性按物理行判定（uk_models_code 不区分 is_deleted），所以逻辑删除后同
     * {@code model_code} 无法新建，只能恢复。恢复时校验绑定的统一模型仍存在且未删除，
     * 避免恢复出一个指向已删除统一模型的模型。</p>
     */
    @Transactional
    public ModelDto restore(Long id) {
        ModelEntity existing = modelMapper.getByIdIncludingDeleted(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商模型不存在");
        }
        if (!Integer.valueOf(1).equals(existing.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONFLICT, "供应商模型未被删除，无需恢复");
        }
        if (existing.getLogicalModelCode() != null
                && logicalModelMapper.getByCode(existing.getLogicalModelCode()) == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "绑定的统一模型已被删除，无法恢复");
        }
        if (modelMapper.restore(id) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "供应商模型恢复失败，请刷新后重试");
        }
        return getById(id);
    }

    /**
     * 单条详情补全：频控额度 + API 绑定 + 编码绑定数。
     *
     * <p>绑定数只在详情路径算（5 次 count），列表路径不带上——列表是分页的多行，
     * 逐行算会变成 N×5 次查询，而列表页并不渲染这个字段。</p>
     */
    private ModelDto enrich(ModelEntity entity) {
        TrafficPolicies traffic = trafficPolicySupport.load(TrafficResourceTypeEnum.MODEL.code(), entity.getId());
        ModelDto base = enrich(modelConverter.toDto(entity), traffic, apiBindingSupport.list(entity.getId()));
        return withBoundCount(base, boundCount(entity.getModelCode()));
    }

    private ModelDto withBoundCount(ModelDto base, Long boundCount) {
        return new ModelDto(
                base.id(),
                base.modelCode(),
                base.upstreamModel(),
                base.providerAccountCode(),
                base.providerName(),
                base.providerCode(),
                base.modelType(),
                base.version(),
                base.basePath(),
                base.remark(),
                base.enabled(),
                base.logicalModelCode(),
                base.billingRuleCode(),
                base.rateLimitPolicy(),
                base.quotaPolicies(),
                base.apiBindings(),
                base.deleted(),
                boundCount
        );
    }

    private static String normalizeModelCode(String modelCode) {
        String code = modelCode != null ? modelCode.trim() : "";
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写模型编码");
        }
        return code;
    }

    private void assertModelCodeAvailable(String modelCode, Long excludeId) {
        if (!isModelCodeAvailable(modelCode, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "模型编码“" + modelCode + "”已存在，请更换后重试");
        }
    }

    private static String normalizeTextToNull(String value) {
        String text = value != null ? value.trim() : "";
        return text.isEmpty() ? null : text;
    }

    private static List<String> normalizeModelTypes(List<String> modelTypes) {
        if (modelTypes == null || modelTypes.isEmpty()) {
            return null;
        }
        List<String> result = modelTypes.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
        return result.isEmpty() ? null : result;
    }

    private ModelDto enrich(ModelDto base, TrafficPolicies traffic, List<ModelApiBindingDto> apiBindings) {
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        List<QuotaPolicy> quotas = traffic != null ? traffic.quotaPolicies() : List.of();
        return new ModelDto(
                base.id(),
                base.modelCode(),
                base.upstreamModel(),
                base.providerAccountCode(),
                base.providerName(),
                base.providerCode(),
                base.modelType(),
                base.version(),
                base.basePath(),
                base.remark(),
                base.enabled(),
                base.logicalModelCode(),
                base.billingRuleCode(),
                rate,
                quotas,
                apiBindings == null ? apiBindingSupport.list(base.id()) : apiBindings,
                base.deleted(),
                base.boundCount()
        );
    }

    /**
     * 供应商模型编码的绑定数：路由目标 + 池内条目 + 厂商模型映射 + API 协议绑定 + 折扣策略
     * + 频控绑定行（resource_type='MODEL' 存 model_code）。
     * 前五类与 {@link #delete(Long)} 的引用拦截同源，第六类是改码守卫独有：
     * 频控只落在 kb_resource_traffic_policies.resource_code 上，改码后多态 join 落空、限流失效。
     */
    private Long boundCount(String modelCode) {
        long count = CodeBindingSupport.countOf(modelMapper.countRoutingTargetsByModelCode(modelCode))
                + CodeBindingSupport.countOf(modelMapper.countPoolItemsByModelCode(modelCode))
                + CodeBindingSupport.countOf(modelMapper.countProviderMappingsByModelCode(modelCode))
                + CodeBindingSupport.countOf(modelMapper.countApiBindingsByModelCode(modelCode))
                + CodeBindingSupport.countOf(discountPolicyMapper.countByModelCode(modelCode))
                + trafficPolicySupport.countBindings(TrafficResourceTypeEnum.MODEL.code(), modelCode);
        return count;
    }

    /**
     * 模型类型不由请求提供，取自绑定统一模型的 modelType。
     */
    private String validateModelRequest(ModelDto request) {
        if (request.providerAccountCode() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择供应商");
        }
        if (request.logicalModelCode() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择统一模型");
        }
        // 上游模型必填：向上游发起请求时写入请求体 model 参数，为空则上游必然拒单
        RequiredTextSupport.require(request.upstreamModel(), "请填写上游模型");
        LogicalModelEntity logicalModel = logicalModelMapper.getByCode(request.logicalModelCode());
        if (logicalModel == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择统一模型");
        }
        if (!EnabledStatusEnum.isEnabled(logicalModel.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只能绑定已启用的统一模型");
        }
        String modelType = ModelTypeEnum.requireCode(logicalModel.getModelType(), "绑定的统一模型缺少模型类型");
        if (request.billingRuleCode() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择计费规则");
        }
        BillingRuleEntity billingRule = billingRuleMapper.getByCode(request.billingRuleCode());
        ProviderAccountEntity account = providerAccountMapper.getByCode(request.providerAccountCode());
        if (billingRule == null || account == null
                || !matchesNullableScope(billingRule.getModelFamilyCode(), logicalModel.getModelFamily())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "计费规则与统一模型不匹配");
        }
        if (request.enabled()) {
            // 上游 host 由供应商账户提供，模型级只保留可选路径前缀，故这里不再校验 Base URL
            RequiredTextSupport.require(request.modelCode(), "请填写模型编码");
        }
        return modelType;
    }

    private static boolean matchesNullableScope(String ruleScopeCode, String selectedCode) {
        return ruleScopeCode == null || ruleScopeCode.equals(selectedCode);
    }

    /**
     * 归一化并校验模型级 Base Path。
     *
     * <p>空串表示「无模型级前缀」，这是绝大多数模型的常态——上游 host 一律由供应商账户提供。
     * 只有供应商给每个模型分配独立路径段（如 Cloudsway 的 endpoint_id）时才需要填。</p>
     *
     * <p>刻意拒绝协议头 / query / fragment / 空格与中文：这些要么说明用户把 host 填错了位置，
     * 要么会让 {@code WebClient.uri(String)} 当 URI 模板解析，导致实际请求地址与预期不符。</p>
     *
     * <p>包级可见：供单测直接驱动归一化规则，不必拖 ModelService 整条依赖链。</p>
     */
    static String normalizeBasePath(String basePath) {
        String value = basePath == null ? "" : basePath.trim();
        if (value.length() > BASE_PATH_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "模型 Base Path 不能超过 " + BASE_PATH_MAX_LENGTH + " 个字符");
        }
        if (value.contains("://")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "模型 Base Path 只能填路径前缀，上游地址请配置在供应商账户上");
        }
        if (value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "模型 Base Path 不能包含 query 或 fragment");
        }
        String normalized = value;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        // 空、空白、"/"、"//" 一律收敛成「无前缀」
        if (normalized.isEmpty() || "/".equals(normalized)) {
            return "";
        }
        if (normalized.charAt(0) != '/') {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型 Base Path 必须以 / 开头");
        }
        if (normalized.contains("//")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型 Base Path 不能包含连续斜杠");
        }
        if (!BASE_PATH_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "模型 Base Path 只能包含字母、数字与 - . _ ~ /");
        }
        return normalized;
    }

    private void saveLogicalModelMapping(Long modelId, String logicalModelCode, String modelCode) {
        logicalModelMapper.deleteMappingsByModelId(modelId);
        ProviderModelMappingEntity mapping = new ProviderModelMappingEntity();
        mapping.setLogicalModelCode(logicalModelCode);
        ModelEntity model = modelMapper.getById(modelId);
        mapping.setProviderAccountCode(model.getProviderAccountCode());
        mapping.setModelCode(model.getModelCode());
        mapping.setProviderModelName(modelCode);
        mapping.setStatus(EnabledStatusEnum.ENABLED.code());
        mapping.setPriority(100);
        logicalModelMapper.insertMapping(mapping);
    }
}
