package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.adapter.request.RequestAdapterCatalog;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.converter.ModelConverter;
import org.chobit.knot.gateway.dto.model.ModelApiBindingDto;
import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderModelMappingEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.LogicalModelMapper;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.usage.UsageExtractorCatalog;
import org.chobit.knot.gateway.vo.model.RequestAdapterItem;
import org.chobit.knot.gateway.vo.model.UsageExtractorItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ModelService {
    private final ModelMapper modelMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;
    private final LogicalModelMapper logicalModelMapper;
    private final BillingRuleMapper billingRuleMapper;
    private final ModelConverter modelConverter;
    private final ResourceTrafficPolicySupport trafficPolicySupport;
    private final UsageExtractorCatalog usageExtractorCatalog;
    private final RequestAdapterCatalog requestAdapterCatalog;

    /**
     * Constructs a new instance.
     */
    public ModelService(ModelMapper modelMapper,
                        ModelApiBindingMapper modelApiBindingMapper,
                        LogicalModelMapper logicalModelMapper,
                        BillingRuleMapper billingRuleMapper,
                        ModelConverter modelConverter,
                        ResourceTrafficPolicySupport trafficPolicySupport,
                        UsageExtractorCatalog usageExtractorCatalog,
                        RequestAdapterCatalog requestAdapterCatalog) {
        this.modelMapper = modelMapper;
        this.modelApiBindingMapper = modelApiBindingMapper;
        this.logicalModelMapper = logicalModelMapper;
        this.billingRuleMapper = billingRuleMapper;
        this.modelConverter = modelConverter;
        this.trafficPolicySupport = trafficPolicySupport;
        this.usageExtractorCatalog = usageExtractorCatalog;
        this.requestAdapterCatalog = requestAdapterCatalog;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ModelDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ModelDto> list(PageRequest pageRequest, String keyword) {
        return list(pageRequest, keyword, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ModelDto> list(PageRequest pageRequest, String keyword, List<String> modelTypes) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ModelEntity> pageInfo = new PageInfo<>(
                modelMapper.list(normalizeKeyword(keyword), normalizeModelTypes(modelTypes))
        );
        List<ModelEntity> entities = pageInfo.getList();
        List<Long> ids = entities.stream().map(ModelEntity::getId).toList();
        Map<Long, TrafficPolicies> trafficMap =
                trafficPolicySupport.loadBatch(TrafficResourceTypeEnum.MODEL.code(), ids);
        Map<Long, List<ModelApiBindingDto>> bindingMap = loadBindingMap(ids);
        List<ModelDto> dtos = entities.stream()
                .map(entity -> enrich(modelConverter.toDto(entity), trafficMap.get(entity.getId()), bindingMap.get(entity.getId())))
                .collect(Collectors.toList());
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
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
                        item.className(),
                        item.streamSupported()
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
                        item.className()
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
        validateModelRequest(request);
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setModelCode(modelCode);
        modelMapper.insert(entity);
        trafficPolicySupport.save(
                TrafficResourceTypeEnum.MODEL.code(),
                entity.getId(),
                request.rateLimitPolicy(),
                request.quotaPolicy()
        );
        saveLogicalModelMapping(entity.getId(), request.logicalModelId(), modelCode);
        saveApiBindings(entity.getId(), request.apiBindings());
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
        assertModelCodeAvailable(modelCode, id);
        validateModelRequest(request);
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setId(id);
        entity.setModelCode(modelCode);
        modelMapper.update(entity);
        trafficPolicySupport.save(
                TrafficResourceTypeEnum.MODEL.code(),
                id,
                request.rateLimitPolicy(),
                request.quotaPolicy()
        );
        saveLogicalModelMapping(id, request.logicalModelId(), modelCode);
        if (request.apiBindings() != null) {
            saveApiBindings(id, request.apiBindings());
        }
        return getById(id);
    }

    /**
     * Updates the model enabled status only.
     */
    @Transactional
    public ModelDto updateStatus(Long id, boolean enabled) {
        ModelDto existing = getById(id);
        ModelDto request = new ModelDto(
                existing.id(),
                existing.modelCode(),
                existing.name(),
                existing.providerId(),
                existing.providerName(),
                existing.modelType(),
                existing.version(),
                existing.baseUrl(),
                enabled,
                existing.logicalModelId(),
                existing.billingRuleId(),
                existing.billingRuleName(),
                existing.rateLimitPolicy(),
                existing.quotaPolicy(),
                existing.apiBindings()
        );
        validateModelRequest(request);
        modelMapper.updateStatus(id, enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        return getById(id);
    }

    private ModelDto enrich(ModelEntity entity) {
        TrafficPolicies traffic = trafficPolicySupport.load(TrafficResourceTypeEnum.MODEL.code(), entity.getId());
        return enrich(modelConverter.toDto(entity), traffic, listApiBindings(entity.getId()));
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

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
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
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        return new ModelDto(
                base.id(),
                base.modelCode(),
                base.name(),
                base.providerId(),
                base.providerName(),
                base.modelType(),
                base.version(),
                base.baseUrl(),
                base.enabled(),
                resolveLogicalModelId(base.id()),
                base.billingRuleId(),
                base.billingRuleName(),
                rate,
                quota,
                apiBindings == null ? listApiBindings(base.id()) : apiBindings
        );
    }

    private void validateModelRequest(ModelDto request) {
        requireText(request.name(), "请填写名称");
        if (request.providerId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择供应商");
        }
        requireText(request.modelType(), "请选择模型类型");
        if (request.logicalModelId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择统一模型");
        }
        LogicalModelEntity logicalModel = logicalModelMapper.getById(request.logicalModelId());
        if (logicalModel == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择统一模型");
        }
        if (!EntityStatusEnum.ENABLED.code().equals(logicalModel.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只能绑定已启用的统一模型");
        }
        String baseUrl = blankToNull(request.baseUrl());
        if (request.billingRuleId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择计费规则");
        }
        BillingRuleEntity billingRule = billingRuleMapper.getById(request.billingRuleId());
        if (billingRule == null
                || !matchesNullableScope(billingRule.getProviderId(), request.providerId())
                || !matchesNullableScope(billingRule.getLogicalModelId(), request.logicalModelId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "计费规则与供应商或统一模型不匹配");
        }
        if (request.enabled()) {
            requireText(request.modelCode(), "请填写模型编码");
            requireText(request.name(), "请填写名称");
            requireText(request.modelType(), "请选择模型类型");
            if (baseUrl == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写 Base URL");
            }
        }
    }

    private static boolean matchesNullableScope(Long ruleScopeId, Long selectedId) {
        return ruleScopeId == null || ruleScopeId.equals(selectedId);
    }

    private static String requireText(String value, String message) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return text;
    }

    private void saveLogicalModelMapping(Long modelId, Long logicalModelId, String modelCode) {
        logicalModelMapper.deleteMappingsByModelId(modelId);
        ProviderModelMappingEntity mapping = new ProviderModelMappingEntity();
        mapping.setLogicalModelId(logicalModelId);
        ModelEntity model = modelMapper.getById(modelId);
        mapping.setProviderId(model.getProviderId());
        mapping.setModelId(modelId);
        mapping.setProviderModelName(modelCode);
        mapping.setStatus(EntityStatusEnum.ENABLED.code());
        mapping.setPriority(100);
        logicalModelMapper.insertMapping(mapping);
    }

    private Long resolveLogicalModelId(Long modelId) {
        List<ProviderModelMappingEntity> mappings = logicalModelMapper.listMappingsByModelId(modelId);
        return mappings.isEmpty() ? null : mappings.get(0).getLogicalModelId();
    }

    private Map<Long, List<ModelApiBindingDto>> loadBindingMap(List<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Map.of();
        }
        return modelApiBindingMapper.listByModelIds(modelIds).stream()
                .map(this::toApiBindingDto)
                .collect(Collectors.groupingBy(
                        ModelApiBindingDtoWithModelId::modelId,
                        LinkedHashMap::new,
                        Collectors.mapping(ModelApiBindingDtoWithModelId::dto, Collectors.toList())
                ));
    }

    private List<ModelApiBindingDto> listApiBindings(Long modelId) {
        if (modelId == null) {
            return List.of();
        }
        return modelApiBindingMapper.listByModelId(modelId).stream()
                .map(this::toApiBindingDto)
                .map(ModelApiBindingDtoWithModelId::dto)
                .toList();
    }

    private ModelApiBindingDtoWithModelId toApiBindingDto(ModelApiBindingEntity entity) {
        ModelApiBindingDto dto = new ModelApiBindingDto(
                entity.getId(),
                entity.getProtocol(),
                entity.getApiPath(),
                entity.getRequestAdapter(),
                entity.getUsageExtractor(),
                entity.getStreamUsageExtractor(),
                EntityStatusEnum.ENABLED.code().equals(entity.getStatus()),
                entity.getRemark()
        );
        return new ModelApiBindingDtoWithModelId(entity.getModelId(), dto);
    }

    private void saveApiBindings(Long modelId, List<ModelApiBindingDto> bindings) {
        modelApiBindingMapper.deleteByModelId(modelId);
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        for (ModelApiBindingDto binding : bindings) {
            String protocol = requireText(binding.protocol(), "请选择接口协议");
            ModelApiBindingEntity entity = new ModelApiBindingEntity();
            entity.setModelId(modelId);
            entity.setProtocol(protocol.trim().toUpperCase());
            entity.setApiPath(blankToNull(binding.apiPath()));
            entity.setRequestAdapter(blankToNull(binding.requestAdapter()));
            entity.setUsageExtractor(blankToDefault(binding.usageExtractor(), "DEFAULT"));
            entity.setStreamUsageExtractor(blankToNull(binding.streamUsageExtractor()));
            entity.setStatus(binding.enabled() ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
            entity.setRemark(blankToNull(binding.remark()));
            modelApiBindingMapper.insert(entity);
        }
    }

    private static String blankToNull(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String blankToDefault(String value, String defaultValue) {
        String text = blankToNull(value);
        return text == null ? defaultValue : text;
    }

    private record ModelApiBindingDtoWithModelId(Long modelId, ModelApiBindingDto dto) {
    }
}
