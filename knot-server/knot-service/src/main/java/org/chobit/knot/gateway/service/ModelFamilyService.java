package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.chobit.knot.gateway.entity.ModelFamilyEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.ModelFamilyMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 模型族维护服务。
 *
 * <p>模型族是模型域概念，但主数据仍落在枚举表 {@code ks_enum_configs}
 * （category={@value #CATEGORY}），与「系统管理 / 枚举管理」同源；本服务只是把它按模型域
 * 单独暴露一套受权限控制的接口，避免为了维护模型族而开放全量枚举的读写权限。</p>
 */
@Service
public class ModelFamilyService {

    /** 模型族在 ks_enum_categories 中的分类编码。 */
    public static final String CATEGORY = "model_family";

    /** 族编码字符集：小写字母/数字开头，允许 - 与 _；保持与 ModelFamilyResolver 的 contains 匹配一致。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]*$");

    private final ModelFamilyMapper modelFamilyMapper;
    private final EnumConfigService enumConfigService;

    /**
     * Constructs a new instance.
     */
    public ModelFamilyService(ModelFamilyMapper modelFamilyMapper, EnumConfigService enumConfigService) {
        this.modelFamilyMapper = modelFamilyMapper;
        this.enumConfigService = enumConfigService;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ModelFamilyEntity> list(PageRequest pageRequest, String keyword) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ModelFamilyEntity> pageInfo = new PageInfo<>(modelFamilyMapper.list(normalizeKeyword(keyword)));
            return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelFamilyEntity getById(Long id) {
        ModelFamilyEntity entity = modelFamilyMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型族不存在");
        }
        return entity;
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    public boolean isCodeAvailable(String code, Long excludeId) {
        String normalized = normalizeCode(code);
        if (normalized == null) {
            return false;
        }
        ModelFamilyEntity existing = modelFamilyMapper.getByCode(normalized);
        return existing == null || (excludeId != null && excludeId.equals(existing.getId()));
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public ModelFamilyEntity create(ModelFamilyEntity request) {
        String code = normalizeCode(request.getCode());
        if (code == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型族编码不能为空");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "模型族编码只能由小写字母、数字、- 与 _ 组成，且以字母或数字开头");
        }
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型族名称不能为空");
        }

        EnumConfigEntity entity = new EnumConfigEntity();
        entity.setCategory(CATEGORY);
        entity.setItemCode(code);
        entity.setItemLabel(name);
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setIsEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(request.getRemark());

        EnumConfigEntity created = enumConfigService.create(entity);
        return getById(created.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ModelFamilyEntity update(Long id, ModelFamilyEntity request) {
        ModelFamilyEntity existing = getById(id);
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型族名称不能为空");
        }

        EnumConfigEntity entity = new EnumConfigEntity();
        entity.setItemLabel(name);
        entity.setSortOrder(request.getSortOrder() == null ? existing.getSortOrder() : request.getSortOrder());
        entity.setIsEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(request.getRemark());

        enumConfigService.update(id, entity);
        return getById(id);
    }

    /**
     * Updates the target resource status. Executes the public operation.
     */
    @Transactional
    public ModelFamilyEntity updateStatus(Long id, boolean enabled) {
        ModelFamilyEntity existing = getById(id);
        EnumConfigEntity entity = new EnumConfigEntity();
        entity.setItemLabel(existing.getName());
        entity.setSortOrder(existing.getSortOrder());
        entity.setIsEnabled(enabled);
        entity.setRemark(existing.getRemark());

        enumConfigService.update(id, entity);
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void delete(Long id) {
        ModelFamilyEntity existing = getById(id);
        int usage = existing.getUsageCount() == null ? 0 : existing.getUsageCount();
        if (usage > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "模型族 " + existing.getCode() + " 已被 " + usage + " 处引用（统一模型 / 计费规则），不可删除");
        }
        enumConfigService.deleteReturning(id);
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
