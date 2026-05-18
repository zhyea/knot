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
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelVersionEntity;
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
    private final ModelConverter modelConverter;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    public ModelService(ModelMapper modelMapper,
                        ModelVersionMapper modelVersionMapper,
                        ModelConverter modelConverter,
                        ResourceTrafficPolicySupport trafficPolicySupport) {
        this.modelMapper = modelMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.modelConverter = modelConverter;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    public PageResult<ModelDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<ModelEntity> pageInfo = new PageInfo<>(modelMapper.list());
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
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setModelCode(modelCode);
        modelMapper.insert(entity);
        trafficPolicySupport.save(TrafficResourceType.MODEL, entity.getId(),
                request.rateLimitPolicy(), request.quotaPolicy());
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
        ModelEntity entity = modelConverter.toEntity(request);
        entity.setId(id);
        entity.setModelCode(modelCode);
        modelMapper.update(entity);
        trafficPolicySupport.save(TrafficResourceType.MODEL, id,
                request.rateLimitPolicy(), request.quotaPolicy());
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

    private ModelDto enrich(ModelDto base, ResourceTrafficPolicySupport.TrafficPolicies traffic) {
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        return new ModelDto(
                base.id(), base.modelCode(), base.name(), base.providerId(), base.providerName(), base.modelType(),
                base.version(), base.enabled(), rate, quota
        );
    }
}
