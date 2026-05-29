package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ModelConverter;
import org.chobit.knot.gateway.constants.TrafficResourceType;
import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.dto.model.ModelTestResultDto;
import org.chobit.knot.gateway.dto.model.ModelVersionSwitchResultDto;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelVersionEntity;
import org.chobit.knot.gateway.entity.ProviderModelMappingEntity;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.LogicalModelMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ModelVersionMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ModelService {
    private final ModelMapper modelMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final LogicalModelMapper logicalModelMapper;
    private final BillingRuleMapper billingRuleMapper;
    private final ModelConverter modelConverter;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    public ModelService(ModelMapper modelMapper,
                        ModelVersionMapper modelVersionMapper,
                        LogicalModelMapper logicalModelMapper,
                        BillingRuleMapper billingRuleMapper,
                        ModelConverter modelConverter,
                        ResourceTrafficPolicySupport trafficPolicySupport) {
        this.modelMapper = modelMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.logicalModelMapper = logicalModelMapper;
        this.billingRuleMapper = billingRuleMapper;
        this.modelConverter = modelConverter;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    public PageResult<ModelDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    public PageResult<ModelDto> list(PageRequest pageRequest, String keyword) {
        return list(pageRequest, keyword, null);
    }

    public PageResult<ModelDto> list(PageRequest pageRequest, String keyword, List<String> modelTypes) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ModelEntity> pageInfo = new PageInfo<>(modelMapper.list(normalizeKeyword(keyword), normalizeModelTypes(modelTypes)));
        List<ModelEntity> entities = pageInfo.getList();
        List<Long> ids = entities.stream().map(ModelEntity::getId).toList();
        Map<Long, ResourceTrafficPolicySupport.TrafficPolicies> trafficMap =
                trafficPolicySupport.loadBatch(TrafficResourceType.MODEL, ids);
        List<ModelDto> dtos = entities.stream()
                .map(e -> enrich(modelConverter.toDto(e), trafficMap.get(e.getId())))
                .collect(Collectors.toList());
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    public ModelDto getById(Long id) {
        ModelEntity entity = modelMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
        }
        return enrich(entity);
    }

    public Map<String, Object> modelAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
        ModelDto dto = getById(id);
        Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", dto.id());
            m.put("name", dto.name());
            m.put("providerId", dto.providerId());
            m.put("modelType", dto.modelType());
            m.put("version", dto.version());
            m.put("enabled", dto.enabled());
            m.put("logicalModelId", dto.logicalModelId());
            m.put("billingRuleId", dto.billingRuleId());
            m.put("billingRuleName", dto.billingRuleName());
            m.put("rateLimitPolicy", dto.rateLimitPolicy());
            m.put("quotaPolicy", dto.quotaPolicy());
            return m;
        } catch (BusinessException e) {
            return null;
        }
    }

    public boolean isModelCodeAvailable(String modelCode, Long excludeId) {
        String code = modelCode != null ? modelCode.trim() : "";
        if (code.isEmpty()) {
            return false;
        }
        Long count = modelMapper.countByModelCode(code, excludeId);
        return count == null || count == 0;
    }

    @Transactional
    public ModelDto create(ModelDto request) {
        String modelCode = normalizeModelCode(request.modelCode());
        assertModelCodeAvailable(modelCode, null);
        validateModelRequest(request);
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setModelCode(modelCode);
        modelMapper.insert(entity);
        trafficPolicySupport.save(TrafficResourceType.MODEL, entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicy());
        saveLogicalModelMapping(entity.getId(), request.logicalModelId(), modelCode);
        if (entity.getVersion() != null) {
            ModelVersionEntity version = new ModelVersionEntity();
            version.setModelId(entity.getId());
            version.setVersion(entity.getVersion());
            version.setGrayPercent(100);
            version.setStatus("ACTIVE");
            modelVersionMapper.insert(version);
        }
        return getById(entity.getId());
    }

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
        trafficPolicySupport.save(TrafficResourceType.MODEL, id,
                request.rateLimitPolicy(), request.quotaPolicy());
        saveLogicalModelMapping(id, request.logicalModelId(), modelCode);
        return getById(id);
    }

    public ModelTestResultDto testModel(Long id, String prompt) {
        ModelDto model = getById(id);
        String output = "[test] " + model.name() + ": " + prompt;
        int latencyMs = 100 + (int) (Math.random() * 200);
        int tokenUsage = Math.max(1, prompt.length() / 2);
        return new ModelTestResultDto(output, latencyMs, tokenUsage);
    }

    @Transactional
    public ModelVersionSwitchResultDto switchVersion(Long id, String targetVersion) {
        getById(id);
        ModelVersionEntity current = modelVersionMapper.getActiveVersion(id);
        if (current != null) {
            current.setStatus("INACTIVE");
            modelVersionMapper.updateStatus(current);
        }
        ModelVersionEntity newVersion = new ModelVersionEntity();
        newVersion.setModelId(id);
        newVersion.setVersion(targetVersion);
        newVersion.setGrayPercent(100);
        newVersion.setStatus("ACTIVE");
        modelVersionMapper.insert(newVersion);
        return new ModelVersionSwitchResultDto(id, targetVersion, "ACTIVE");
    }

    private ModelDto enrich(ModelEntity entity) {
        ResourceTrafficPolicySupport.TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceType.MODEL, entity.getId());
        return enrich(modelConverter.toDto(entity), traffic);
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
            throw new BusinessException(ErrorCode.CONFLICT,
                    "模型编码「" + modelCode + "」已存在，请更换后重试");
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

    private ModelDto enrich(ModelDto base, ResourceTrafficPolicySupport.TrafficPolicies traffic) {
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        return new ModelDto(
                base.id(), base.modelCode(), base.name(), base.providerId(), base.providerName(), base.modelType(),
                base.version(), base.enabled(), resolveLogicalModelId(base.id()), base.billingRuleId(), base.billingRuleName(), rate, quota
        );
    }

    private void validateModelRequest(ModelDto request) {
        requireText(request.name(), "璇峰～鍐欏悕绉?");
        if (request.providerId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "璇烽€夋嫨渚涘簲鍟?");
        }
        requireText(request.modelType(), "璇烽€夋嫨妯″瀷绫诲瀷");
        if (request.logicalModelId() == null || logicalModelMapper.getById(request.logicalModelId()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "璇烽€夋嫨缁熶竴妯″瀷");
        }
        if (request.billingRuleId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "billing rule is required");
        }
        BillingRuleEntity billingRule = billingRuleMapper.getById(request.billingRuleId());
        if (billingRule == null
                || !matchesNullableScope(billingRule.getProviderId(), request.providerId())
                || !matchesNullableScope(billingRule.getLogicalModelId(), request.logicalModelId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "billing rule does not match provider or logical model");
        }
        if (request.enabled()) {
            requireText(request.modelCode(), "璇峰～鍐欐ā鍨嬬紪鐮?");
            requireText(request.name(), "璇峰～鍐欏悕绉?");
            requireText(request.modelType(), "璇烽€夋嫨妯″瀷绫诲瀷");
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
        mapping.setStatus("ENABLED");
        mapping.setPriority(100);
        logicalModelMapper.insertMapping(mapping);
    }

    private Long resolveLogicalModelId(Long modelId) {
        List<ProviderModelMappingEntity> mappings = logicalModelMapper.listMappingsByModelId(modelId);
        return mappings.isEmpty() ? null : mappings.get(0).getLogicalModelId();
    }
}
