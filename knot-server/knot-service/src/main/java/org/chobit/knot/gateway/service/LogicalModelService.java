package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.constants.enums.LogicalModelPublishStatusEnum;
import org.chobit.knot.gateway.constants.enums.LogicalModelVisibilityEnum;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.chobit.knot.gateway.converter.LogicalModelConverter;
import org.chobit.knot.gateway.dto.model.LogicalModelDto;
import org.chobit.knot.gateway.dto.model.ProviderModelMappingDto;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderModelMappingEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.mapper.ExternalModelMapper;
import org.chobit.knot.gateway.mapper.LogicalModelMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return list(pageRequest, null, null, null);
    }

    /**
     * Returns matching results. Executes the public operation.
     */
    public PageResult<LogicalModelDto> list(PageRequest pageRequest, String keyword) {
        return list(pageRequest, keyword, null, null);
    }

    /**
     * Returns matching results. Executes the public operation.
     *
     * @param includeDeleted 管理列表传 true 以便展示已删除行（浅红底 + 恢复按钮）；
     *                       下拉/绑定类查询保持 false，已删除项不列为备选
     */
    public PageResult<LogicalModelDto> list(PageRequest pageRequest,
                                            String keyword,
                                            List<String> modelTypes,
                                            Boolean includeDeleted) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<LogicalModelEntity> pageInfo = new PageInfo<>(
                    logicalModelMapper.list(normalizeKeyword(keyword), normalizeModelTypes(modelTypes), includeDeleted)
            );
            List<LogicalModelDto> list = pageInfo.getList().stream()
                    .map(logicalModelConverter::toDto)
                    .toList();
            return PageResult.of(list, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
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

    /**
     * Returns the requested value. Executes the public operation.
     */
    public LogicalModelDto getById(Long id) {
        LogicalModelEntity entity = logicalModelMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
        LogicalModelDto base = logicalModelConverter.toDto(entity);
        return logicalModelConverter.withMappings(base, listMappings(entity.getModelCode()));
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
        entity.setModelType(ModelTypeEnum.requireCode(request.modelType(), "unsupported model type"));
        entity.setVisibility(resolveVisibility(request.visibility()));
        entity.setPublishStatus(resolvePublishStatus(request.publishStatus()));
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
        entity.setModelType(ModelTypeEnum.requireCode(request.modelType(), "unsupported model type"));
        entity.setVisibility(resolveVisibility(request.visibility()));
        entity.setPublishStatus(resolvePublishStatus(request.publishStatus()));
        logicalModelMapper.update(entity);
        return getById(id);
    }

    /**
     * Updates the logical model enabled status only.
     */
    @Transactional
    public LogicalModelDto updateStatus(Long id, boolean enabled) {
        if (logicalModelMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
        logicalModelMapper.updateStatus(id, EnabledStatusEnum.codeOf(enabled));
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void delete(Long id) {
        LogicalModelEntity entity = logicalModelMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
        if (EnabledStatusEnum.isEnabled(entity.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "启用中的统一模型不能删除，请先停用");
        }
        long refCount = logicalModelMapper.countMappingsByLogicalModelCode(entity.getModelCode());
        Long itemRefCount = externalModelMapper.countByLogicalModelId(id);
        refCount += itemRefCount == null ? 0 : itemRefCount;
        if (refCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该统一模型已被供应商模型或外部模型引用，无法删除");
        }
        int affected = logicalModelMapper.logicalDelete(id);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
    }

    /**
     * Restores a logically deleted logical model.
     *
     * <p>编码唯一性按物理行判定（uk_logical_models_code 不区分 is_deleted），所以逻辑删除后
     * 同 {@code model_code} 无法新建，只能恢复。
     */
    @Transactional
    public LogicalModelDto restore(Long id) {
        LogicalModelEntity entity = logicalModelMapper.getByIdIncludingDeleted(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
        if (!Integer.valueOf(1).equals(entity.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONFLICT, "统一模型未被删除，无需恢复");
        }
        if (logicalModelMapper.restore(id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "logical model not found");
        }
        return getById(id);
    }

    /**
     * Builds the audit snapshot recorded by {@code @OperationLog}.
     * Evaluated by the {@code @Around} aspect before the method body runs, so a logical
     * delete still captures the full pre-delete state. Returns null once the model is gone
     * (repeated delete) or the id is null.
     */
    public Map<String, Object> logicalModelAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            // 含已删除：恢复操作要取删除前快照，删除操作要取删除瞬间状态
            LogicalModelEntity entity = logicalModelMapper.getByIdIncludingDeleted(id);
            return entity == null ? null : JsonKit.toMap(logicalModelConverter.toDto(entity));
        } catch (BusinessException e) {
            return null;
        }
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<ProviderModelMappingDto> listMappings(String logicalModelCode) {
        ensureLogicalModel(logicalModelCode);
        return logicalModelMapper.listMappings(logicalModelCode).stream()
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
    public ProviderModelMappingDto createMapping(String logicalModelCode, ProviderModelMappingDto request) {
        ensureLogicalModel(logicalModelCode);
        ProviderModelMappingEntity entity = logicalModelConverter.toMappingEntity(request);
        entity.setLogicalModelCode(logicalModelCode);
        enrichMappingFromModel(entity);
        logicalModelMapper.insertMapping(entity);
        return logicalModelConverter.toMappingDto(logicalModelMapper.getMappingById(entity.getId()));
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ProviderModelMappingDto updateMapping(String logicalModelCode, Long mappingId, ProviderModelMappingDto request) {
        ensureLogicalModel(logicalModelCode);
        ProviderModelMappingEntity existing = logicalModelMapper.getMappingById(mappingId);
        if (existing == null || !logicalModelCode.equals(existing.getLogicalModelCode())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model mapping not found");
        }
        ProviderModelMappingEntity entity = logicalModelConverter.toMappingEntity(request);
        entity.setId(mappingId);
        entity.setLogicalModelCode(logicalModelCode);
        enrichMappingFromModel(entity);
        logicalModelMapper.updateMapping(entity);
        return logicalModelConverter.toMappingDto(logicalModelMapper.getMappingById(mappingId));
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void deleteMapping(String logicalModelCode, Long mappingId) {
        int affected = logicalModelMapper.deleteMapping(logicalModelCode, mappingId);
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
        mapping.setProviderAccountCode(model.getProviderAccountCode());
        if (mapping.getProviderModelName() == null || mapping.getProviderModelName().isBlank()) {
            mapping.setProviderModelName(model.getModelCode());
        }
    }

    private void ensureLogicalModel(String code) {
        if (logicalModelMapper.getByCode(code) == null) {
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

    /**
     * 可见性：空白取默认 PUBLIC，非空必须命中代码枚举，否则拒绝。
     */
    private static String resolveVisibility(String value) {
        if (value == null || value.isBlank()) {
            return LogicalModelVisibilityEnum.defaultVisibility().code();
        }
        return LogicalModelVisibilityEnum.requireCode(value, "unsupported visibility: " + value);
    }

    /**
     * 发布状态：null 取默认 DRAFT，非空必须命中代码枚举，否则拒绝。
     */
    private static Integer resolvePublishStatus(Integer value) {
        if (value == null) {
            return LogicalModelPublishStatusEnum.defaultStatus().code();
        }
        return LogicalModelPublishStatusEnum.requireCode(value, "unsupported publish status: " + value);
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
