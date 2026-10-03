package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.chobit.knot.gateway.converter.ModelPoolConverter;
import org.chobit.knot.gateway.dto.model.ModelPoolDto;
import org.chobit.knot.gateway.dto.model.ModelPoolItemDto;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ModelPoolMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ModelPoolService {

    private final ModelPoolMapper modelPoolMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolConverter modelPoolConverter;

    /**
     * Constructs a new instance.
     */
    public ModelPoolService(ModelPoolMapper modelPoolMapper,
                            ModelMapper modelMapper,
                            ModelPoolConverter modelPoolConverter) {
        this.modelPoolMapper = modelPoolMapper;
        this.modelMapper = modelMapper;
        this.modelPoolConverter = modelPoolConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ModelPoolDto> list(PageRequest pageRequest, String keyword, List<String> modelTypes) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ModelPoolEntity> pageInfo =
                    new PageInfo<>(modelPoolMapper.list(normalizeKeyword(keyword), normalizeModelTypes(modelTypes)));
            List<ModelPoolDto> dtos = pageInfo.getList().stream()
                    .map(entity -> enrich(modelPoolConverter.toDto(entity)))
                    .toList();
            return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelPoolDto getById(Long id) {
        ModelPoolEntity entity = modelPoolMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
        }
        return enrich(modelPoolConverter.toDto(entity));
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isPoolCodeAvailable(String poolCode, Long excludeId) {
        String code = normalizePoolCode(poolCode);
        if (code.isEmpty()) {
            return false;
        }
        Long count = modelPoolMapper.countByPoolCode(code, excludeId);
        return count == null || count == 0;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ModelPoolDto create(ModelPoolDto request) {
        validateForSave(request, null);
        ModelPoolEntity entity = modelPoolConverter.toEntity(normalize(request));
        modelPoolMapper.insert(entity);
        saveItems(entity.getPoolCode(), request.items());
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ModelPoolDto update(Long id, ModelPoolDto request) {
        if (modelPoolMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
        }
        validateForSave(request, id);
        ModelPoolEntity entity = modelPoolConverter.toEntity(normalize(request));
        entity.setId(id);
        modelPoolMapper.update(entity);
        saveItems(entity.getPoolCode(), request.items());
        return getById(id);
    }

    /**
     * Updates the model pool enabled status only.
     */
    @Transactional
    public ModelPoolDto updateStatus(Long id, boolean enabled) {
        ModelPoolDto existing = getById(id);
        ModelPoolDto request = new ModelPoolDto(
                existing.id(),
                existing.poolCode(),
                existing.name(),
                existing.modelType(),
                existing.selectionStrategy(),
                enabled,
                existing.remark(),
                existing.items()
        );
        validateForSave(request, id);
        modelPoolMapper.updateStatus(id, enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void delete(Long id) {
        ModelPoolEntity existing = modelPoolMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
        }
        Long refCount = modelPoolMapper.countRoutingTargetsByPoolId(id);
        if (refCount != null && refCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该模型池已被路由规则引用，无法删除");
        }
        modelPoolMapper.logicalDelete(id);
    }

    /**
     * Builds the audit snapshot recorded by {@code @OperationLog}.
     * Returns null when the pool no longer exists, which is expected after a delete.
     */
    public Map<String, Object> modelPoolAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            ModelPoolDto dto = getById(id);
            return JsonKit.toMap(dto);
        } catch (BusinessException e) {
            return null;
        }
    }

    private ModelPoolDto enrich(ModelPoolDto dto) {
        List<ModelPoolItemDto> items = modelPoolMapper.listItemsByPoolCode(dto.poolCode()).stream()
                .map(modelPoolConverter::toItemDto)
                .toList();
        return new ModelPoolDto(
                dto.id(),
                dto.poolCode(),
                dto.name(),
                dto.modelType(),
                dto.selectionStrategy(),
                dto.enabled(),
                dto.remark(),
                items
        );
    }

    private void saveItems(String poolCode, List<ModelPoolItemDto> items) {
        modelPoolMapper.deleteItemsByPoolCode(poolCode);
        if (items == null) {
            return;
        }
        for (ModelPoolItemDto item : items) {
            ModelPoolItemEntity entity = new ModelPoolItemEntity();
            entity.setPoolCode(poolCode);
            entity.setModelCode(item.modelCode());
            entity.setWeight(defaultInt(item.weight(), 100));
            entity.setPriority(defaultInt(item.priority(), 100));
            entity.setStatus(item.enabled() ? "ENABLED" : "DISABLED");
            modelPoolMapper.insertItem(entity);
        }
    }

    private void validateForSave(ModelPoolDto request, Long excludeId) {
        String poolCode = normalizePoolCode(request.poolCode());
        requireText(poolCode, "please input pool code");
        requireText(request.name(), "please input pool name");
        ModelTypeEnum.requireCode(request.modelType(), "unsupported model type");
        ModelPoolSelectionStrategyEnum.requireCode(request.selectionStrategy(), "unsupported selection strategy");
        if (!isPoolCodeAvailable(poolCode, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "model pool code already exists");
        }
        validateItems(request);
    }

    private void validateItems(ModelPoolDto request) {
        List<ModelPoolItemDto> items = request.items() == null ? List.of() : request.items();
        if (request.enabled() && items.stream().noneMatch(ModelPoolItemDto::enabled)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "enabled model pool requires at least one enabled model");
        }
        long distinct = items.stream().map(ModelPoolItemDto::modelCode).distinct().count();
        if (distinct != items.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model pool item cannot repeat");
        }
        for (ModelPoolItemDto item : items) {
            if (item.modelCode() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please select model");
            }
            ModelEntity model = modelMapper.getByCode(item.modelCode());
            if (model == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
            }
            if (!normalizeText(request.modelType()).equals(model.getModelType())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model type must match model pool type");
            }
            if (request.enabled() && item.enabled() && !"ENABLED".equals(model.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "enabled model pool cannot bind disabled model");
            }
            if (defaultInt(item.weight(), 100) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model weight must be greater than 0");
            }
        }
    }

    private ModelPoolDto normalize(ModelPoolDto request) {
        return new ModelPoolDto(
                request.id(),
                normalizePoolCode(request.poolCode()),
                normalizeText(request.name()),
                ModelTypeEnum.requireCode(request.modelType(), "unsupported model type"),
                ModelPoolSelectionStrategyEnum.requireCode(request.selectionStrategy(), "unsupported selection strategy"),
                request.enabled(),
                normalizeNullable(request.remark()),
                request.items()
        );
    }

    private static String normalizePoolCode(String poolCode) {
        return normalizeText(poolCode);
    }

    private static String normalizeKeyword(String keyword) {
        String value = normalizeText(keyword);
        return value.isEmpty() ? null : value;
    }

    private static List<String> normalizeModelTypes(List<String> modelTypes) {
        if (modelTypes == null || modelTypes.isEmpty()) {
            return null;
        }
        List<String> result = modelTypes.stream()
                .map(ModelPoolService::normalizeText)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
        return result.isEmpty() ? null : result;
    }

    private static String requireText(String value, String message) {
        String text = normalizeText(value);
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return text;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeNullable(String value) {
        String text = normalizeText(value);
        return text.isEmpty() ? null : text;
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
