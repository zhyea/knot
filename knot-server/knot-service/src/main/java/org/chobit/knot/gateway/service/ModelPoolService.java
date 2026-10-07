package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.gateway.converter.ModelPoolConverter;
import org.chobit.knot.gateway.dto.model.ModelPoolDto;
import org.chobit.knot.gateway.dto.model.ModelPoolItemDto;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.LogicalModelMapper;
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
    private final LogicalModelMapper logicalModelMapper;
    private final ModelPoolConverter modelPoolConverter;

    /**
     * Constructs a new instance.
     */
    public ModelPoolService(ModelPoolMapper modelPoolMapper,
                            ModelMapper modelMapper,
                            LogicalModelMapper logicalModelMapper,
                            ModelPoolConverter modelPoolConverter) {
        this.modelPoolMapper = modelPoolMapper;
        this.modelMapper = modelMapper;
        this.logicalModelMapper = logicalModelMapper;
        this.modelPoolConverter = modelPoolConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     *
     * @param includeDeleted 管理列表传 true 以便展示已删除行（浅红底 + 恢复按钮）；
     *                       下拉/选择类查询保持 false，已删除项不列为备选
     */
    public PageResult<ModelPoolDto> list(PageRequest pageRequest,
                                         String keyword,
                                         List<String> modelTypes,
                                         Boolean includeDeleted,
                                         String status) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ModelPoolEntity> pageInfo = new PageInfo<>(modelPoolMapper.list(
                    normalizeKeyword(keyword), normalizeModelTypes(modelTypes), includeDeleted,
                    normalizeTextToNull(status)));
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
        return toDto(entity);
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
        ModelPoolEntity existing = modelPoolMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
        }
        // 改绑统一模型的前置检查先于逐项校验：池内非空时先给出「请先清空」的准确提示，
        // 否则会被「池内模型必须属于同一统一模型」盖住。
        String logicalModelCode = requireText(
                normalizeLogicalModelCode(request.logicalModelCode()),
                "please select logical model"
        );
        if (!logicalModelCode.equals(normalizeText(existing.getLogicalModelCode()))
                && !modelPoolMapper.listItemsByPoolCode(existing.getPoolCode()).isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "切换统一模型前请先清空池内模型");
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
                existing.logicalModelCode(),
                existing.logicalModelName(),
                existing.modelType(),
                existing.selectionStrategy(),
                enabled,
                existing.deleted(),
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
        Long refCount = modelPoolMapper.countRoutingTargetsByPoolCode(existing.getPoolCode());
        if (refCount != null && refCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该模型池已被路由规则引用，无法删除");
        }
        modelPoolMapper.logicalDelete(id);
    }

    /**
     * Restores a logically deleted model pool.
     *
     * <p>编码唯一性按物理行判定（uk_model_pools_code 不区分 is_deleted），所以逻辑删除后同
     * {@code pool_code} 无法新建，只能恢复。恢复时校验绑定的统一模型仍存在且未删除，
     * 避免恢复出一个指向已删除统一模型的池。
     */
    @Transactional
    public ModelPoolDto restore(Long id) {
        ModelPoolEntity existing = modelPoolMapper.getByIdIncludingDeleted(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
        }
        if (!Integer.valueOf(1).equals(existing.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONFLICT, "模型池未被删除，无需恢复");
        }
        LogicalModelEntity logicalModel = logicalModelMapper.getByCode(existing.getLogicalModelCode());
        if (logicalModel == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "绑定的统一模型已被删除，无法恢复");
        }
        if (modelPoolMapper.restore(id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
        }
        return getById(id);
    }

    /**
     * Builds the audit snapshot recorded by {@code @OperationLog}.
     * Evaluated by the {@code @Around} aspect before the method body runs, so a logical
     * delete still captures the full pre-delete state. Returns null once the pool is gone
     * (repeated delete) or the id is null.
     */
    public Map<String, Object> modelPoolAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            // 含已删除：恢复操作要取删除前快照，删除操作要取删除瞬间状态
            ModelPoolEntity entity = modelPoolMapper.getByIdIncludingDeleted(id);
            return entity == null ? null : JsonKit.toMap(toDto(entity));
        } catch (BusinessException e) {
            return null;
        }
    }

    private ModelPoolDto toDto(ModelPoolEntity entity) {
        return enrich(modelPoolConverter.toDto(entity));
    }

    private ModelPoolDto enrich(ModelPoolDto dto) {
        List<ModelPoolItemDto> items = modelPoolMapper.listItemsByPoolCode(dto.poolCode()).stream()
                .map(modelPoolConverter::toItemDto)
                .toList();
        return new ModelPoolDto(
                dto.id(),
                dto.poolCode(),
                dto.name(),
                dto.logicalModelCode(),
                dto.logicalModelName(),
                dto.modelType(),
                dto.selectionStrategy(),
                dto.enabled(),
                dto.deleted(),
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
        // 新建模型池强制 pool 前缀约定；编辑既有池（历史数据可能为 -pool 后缀）豁免
        if (excludeId == null && !poolCode.toLowerCase().startsWith("pool")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model pool code must start with 'pool'");
        }
        requireText(request.name(), "please input pool name");
        String logicalModelCode = requireText(
                normalizeLogicalModelCode(request.logicalModelCode()),
                "please select logical model"
        );
        LogicalModelEntity logicalModel = logicalModelMapper.getByCode(logicalModelCode);
        if (logicalModel == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "logical model not found");
        }
        if (EntityStatusEnum.ENABLED.code().equals(request.enabled())
                && !EntityStatusEnum.ENABLED.code().equals(logicalModel.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只能绑定已启用的统一模型");
        }
        ModelPoolSelectionStrategyEnum.requireCode(request.selectionStrategy(), "unsupported selection strategy");
        if (!isPoolCodeAvailable(poolCode, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "model pool code already exists");
        }
        validateItems(request, logicalModelCode);
    }

    /**
     * 池内模型必须全部映射到池绑定的同一个统一模型：逐个按 model_code 反查
     * kb_provider_model_mappings.logical_model_code 比对。
     */
    private void validateItems(ModelPoolDto request, String logicalModelCode) {
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
            String boundLogicalModelCode = model.getLogicalModelCode();
            if (boundLogicalModelCode == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型未绑定统一模型，无法加入模型池");
            }
            if (!logicalModelCode.equals(boundLogicalModelCode)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "一个模型池中的模型必须属于同一个统一模型"
                );
            }
            if (request.enabled() && item.enabled() && !"ENABLED".equals(model.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "enabled model pool cannot bind disabled model");
            }
            if (defaultInt(item.weight(), 100) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "model weight must be greater than 0");
            }
        }
    }

    /**
     * modelType / logicalModelName 由绑定统一模型派生，不接受请求值：置 null，
     * 由 {@code toEntity} 的 ignore 语义保证不落库，查询时再从统一模型派生。
     */
    private ModelPoolDto normalize(ModelPoolDto request) {
        return new ModelPoolDto(
                request.id(),
                normalizePoolCode(request.poolCode()),
                normalizeText(request.name()),
                normalizeLogicalModelCode(request.logicalModelCode()),
                null,
                null,
                ModelPoolSelectionStrategyEnum.requireCode(request.selectionStrategy(), "unsupported selection strategy"),
                request.enabled(),
                false,
                normalizeNullable(request.remark()),
                request.items()
        );
    }

    private static String normalizeLogicalModelCode(String logicalModelCode) {
        return normalizeText(logicalModelCode);
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

    private static String normalizeTextToNull(String value) {
        String text = value != null ? value.trim() : "";
        return text.isEmpty() ? null : text;
    }

    private static String normalizeNullable(String value) {
        String text = normalizeText(value);
        return text.isEmpty() ? null : text;
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
