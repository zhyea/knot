package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.converter.LogicalModelConverter;
import org.chobit.knot.gateway.dto.model.LogicalModelDto;
import org.chobit.knot.gateway.dto.model.ProviderModelMappingDto;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderModelMappingEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.ExternalModelMapper;
import org.chobit.knot.gateway.mapper.LogicalModelMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogicalModelService {
    private final LogicalModelMapper logicalModelMapper;
    private final ModelMapper modelMapper;
    private final ExternalModelMapper externalModelMapper;
    private final LogicalModelConverter logicalModelConverter;

    /**
     * Constructs a new instance.
     */
    public LogicalModelService(LogicalModelMapper logicalModelMapper,
                               ModelMapper modelMapper,
                               ExternalModelMapper externalModelMapper,
                               LogicalModelConverter logicalModelConverter) {
        this.logicalModelMapper = logicalModelMapper;
        this.modelMapper = modelMapper;
        this.externalModelMapper = externalModelMapper;
        this.logicalModelConverter = logicalModelConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<LogicalModelDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<LogicalModelDto> list(PageRequest pageRequest, String keyword) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<LogicalModelEntity> pageInfo = new PageInfo<>(logicalModelMapper.list(normalizeKeyword(keyword)));
        List<LogicalModelDto> list = pageInfo.getList().stream()
                .map(logicalModelConverter::toDto)
                .toList();
        return PageResult.of(list, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public LogicalModelDto getById(Long id) {
        LogicalModelEntity entity = logicalModelMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
        LogicalModelDto base = logicalModelConverter.toDto(entity);
        return logicalModelConverter.withMappings(base, listMappings(id));
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isModelCodeAvailable(String modelCode, Long excludeId) {
        String code = normalizeCode(modelCode);
        if (code.isEmpty()) {
            return false;
        }
        Long count = logicalModelMapper.countByModelCode(code, excludeId);
        return count == null || count == 0;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public LogicalModelDto create(LogicalModelDto request) {
        String code = requireCode(request.modelCode());
        assertModelCodeAvailable(code, null);
        LogicalModelEntity entity = logicalModelConverter.toEntity(request);
        entity.setModelCode(code);
        entity.setModelName(requireText(request.modelName(), "model name is required"));
        entity.setModelType(requireText(request.modelType(), "model type is required"));
        logicalModelMapper.insert(entity);
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public LogicalModelDto update(Long id, LogicalModelDto request) {
        LogicalModelEntity existing = logicalModelMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
        String code = requireCode(request.modelCode());
        assertModelCodeAvailable(code, id);
        LogicalModelEntity entity = logicalModelConverter.toEntity(request);
        entity.setId(id);
        entity.setModelCode(code);
        entity.setModelName(requireText(request.modelName(), "model name is required"));
        entity.setModelType(requireText(request.modelType(), "model type is required"));
        logicalModelMapper.update(entity);
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void delete(Long id) {
        ensureLogicalModel(id);
        externalModelMapper.clearLogicalModelMatch(id);
        logicalModelMapper.deleteMappingsByLogicalModelId(id);
        int affected = logicalModelMapper.deleteById(id);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public Map<String, Object> logicalModelAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            LogicalModelDto dto = getById(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", dto.id());
            m.put("modelCode", dto.modelCode());
            m.put("modelName", dto.modelName());
            m.put("modelType", dto.modelType());
            m.put("publishStatus", dto.publishStatus());
            m.put("enabled", dto.enabled());
            m.put("featured", dto.featured());
            m.put("mappingCount", dto.mappings() == null ? 0 : dto.mappings().size());
            return m;
        } catch (BusinessException e) {
            return null;
        }
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ProviderModelMappingDto> listMappings(Long logicalModelId) {
        ensureLogicalModel(logicalModelId);
        return logicalModelMapper.listMappings(logicalModelId).stream()
                .map(logicalModelConverter::toMappingDto)
                .toList();
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ProviderModelMappingDto> listMappingsByProviderModel(Long modelId) {
        ensureProviderModel(modelId);
        return logicalModelMapper.listMappingsByModelId(modelId).stream()
                .map(logicalModelConverter::toMappingDto)
                .toList();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ProviderModelMappingDto createMapping(Long logicalModelId, ProviderModelMappingDto request) {
        ensureLogicalModel(logicalModelId);
        ProviderModelMappingEntity entity = logicalModelConverter.toMappingEntity(request);
        entity.setLogicalModelId(logicalModelId);
        enrichMappingFromModel(entity);
        logicalModelMapper.insertMapping(entity);
        return logicalModelConverter.toMappingDto(logicalModelMapper.getMappingById(entity.getId()));
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ProviderModelMappingDto updateMapping(Long logicalModelId, Long mappingId, ProviderModelMappingDto request) {
        ensureLogicalModel(logicalModelId);
        ProviderModelMappingEntity existing = logicalModelMapper.getMappingById(mappingId);
        if (existing == null || !logicalModelId.equals(existing.getLogicalModelId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model mapping not found");
        }
        ProviderModelMappingEntity entity = logicalModelConverter.toMappingEntity(request);
        entity.setId(mappingId);
        entity.setLogicalModelId(logicalModelId);
        enrichMappingFromModel(entity);
        logicalModelMapper.updateMapping(entity);
        return logicalModelConverter.toMappingDto(logicalModelMapper.getMappingById(mappingId));
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void deleteMapping(Long logicalModelId, Long mappingId) {
        int affected = logicalModelMapper.deleteMapping(logicalModelId, mappingId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model mapping not found");
        }
    }

    private void enrichMappingFromModel(ProviderModelMappingEntity mapping) {
        if (mapping.getModelId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model is required");
        }
        ModelEntity model = modelMapper.getById(mapping.getModelId());
        if (model == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provider model not found");
        }
        mapping.setProviderId(model.getProviderId());
        if (mapping.getProviderModelName() == null || mapping.getProviderModelName().isBlank()) {
            mapping.setProviderModelName(model.getModelCode());
        }
    }

    private void ensureLogicalModel(Long id) {
        if (logicalModelMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
    }

    private void ensureProviderModel(Long id) {
        if (id == null || modelMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provider model not found");
        }
    }

    private static String requireCode(String value) {
        String code = normalizeCode(value);
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model code is required");
        }
        if (code.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model code length must be <= 128");
        }
        return code;
    }

    private static String requireText(String value, String message) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return text;
    }

    private static String normalizeCode(String value) {
        return value != null ? value.trim() : "";
    }

    private void assertModelCodeAvailable(String modelCode, Long excludeId) {
        if (!isModelCodeAvailable(modelCode, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "logical model code already exists");
        }
    }
}
