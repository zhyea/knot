package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.entity.EnumCategoryEntity;
import org.chobit.knot.gateway.entity.EnumCategorySummary;
import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.EnumCategoryMapper;
import org.chobit.knot.gateway.mapper.EnumConfigMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class EnumConfigService {

    /**
     * 已由后端代码枚举（GET /api/common/enums，EnumOptionRegistry）权威维护的 DB 分类编码。
     * 这些分类已在 data.sql 与迁移脚本 2026-10-02-retire-code-enums.sql 中退役，
     * 禁止在 DB 重新创建，否则会再次产生"双源漂移"。
     */
    private static final Set<String> CODE_ENUM_CATEGORY_CODES = Set.of(
            "app_type",
            "billing_unit",
            "billing_currency",
            "plugin_scope_type",
            "status",
            "logical_model_visibility",
            "logical_model_publish_status",
            "model_pool_selection_strategy",
            "plugin_extension_point",
            "plugin_stage_code"
    );

    /**
     * 零消费孤儿分类：自建库起就没有任何前后端/Mapper 读取方，属于"可配置但没人配置"的
     * 误配置入口，已在迁移脚本 2026-10-03-retire-orphan-enums.sql 中整体删除。
     * 与 {@link #CODE_ENUM_CATEGORY_CODES} 的区别是它们**没有**代码枚举接管（不存在"正确取值"），
     * 但同样禁止重建——若将来真要启用（如告警级别进功能），应走「新建 Java enum + 注册
     * EnumOptionRegistry」的标准路径，而不是在枚举管理页手工填回这几张字典表。
     */
    private static final Set<String> RETIRED_ORPHAN_CATEGORY_CODES = Set.of(
            "plugin_source_type",
            "alert_level",
            "risk_level",
            "plugin_fail_mode",
            "plugin_result_status"
    );

    /** 禁止在 DB 重新创建的分类（代码枚举已接管的 + 已退役孤儿的）。 */
    private static boolean isReservedCategory(String categoryCode) {
        return CODE_ENUM_CATEGORY_CODES.contains(categoryCode)
                || RETIRED_ORPHAN_CATEGORY_CODES.contains(categoryCode);
    }


    private final EnumConfigMapper enumConfigMapper;
    private final EnumCategoryMapper enumCategoryMapper;

    /**
     * Constructs a new instance.
     */
    public EnumConfigService(EnumConfigMapper enumConfigMapper, EnumCategoryMapper enumCategoryMapper) {
        this.enumConfigMapper = enumConfigMapper;
        this.enumCategoryMapper = enumCategoryMapper;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<EnumConfigEntity> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<EnumConfigEntity> list(PageRequest pageRequest, String category) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<EnumConfigEntity> pageInfo = new PageInfo<>(
                    category != null && !category.isBlank()
                            ? enumConfigMapper.listByCategoryFilter(category)
                            : enumConfigMapper.list()
            );
            return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<EnumConfigEntity> listByCategory(String category) {
        EnumCategoryEntity cat = enumCategoryMapper.selectByCategory(category);
        if (cat == null) {
            return List.of();
        }
        List<EnumConfigEntity> items = enumConfigMapper.listByCategoryId(cat.getId());
        for (EnumConfigEntity item : items) {
            item.setCategory(cat.getCategory());
            item.setIsSystem(cat.getIsSystem());
        }
        return items;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<String> listCategories() {
        return enumConfigMapper.listCategories();
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public List<EnumCategorySummary> listCategorySummaries(String keyword) {
        return enumConfigMapper.listCategorySummaries(normalizeKeyword(keyword));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public EnumConfigEntity getById(Long id) {
        EnumConfigEntity entity = enumConfigMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "enum config not found");
        }
        return entity;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public EnumConfigEntity create(EnumConfigEntity request) {
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类编码不能为空");
        }
        String categoryCode = request.getCategory().trim();
        if (isReservedCategory(categoryCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "枚举分类 " + categoryCode + " 已退役（代码枚举接管或零消费孤儿），禁止在 DB 重建");
        }
        EnumConfigEntity existing = enumConfigMapper.getByCategoryAndCode(categoryCode, request.getItemCode());
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "枚举项 " + categoryCode + "/" + request.getItemCode() + " 已存在");
        }
        Long categoryId = resolveOrCreateCategoryId(categoryCode);
        request.setCategoryId(categoryId);
        if (request.getSortOrder() == null) {
            request.setSortOrder(0);
        }
        if (request.getIsEnabled() == null) {
            request.setIsEnabled(true);
        }
        enumConfigMapper.insert(request);
        request.setCategory(categoryCode);
        return request;
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public EnumConfigEntity update(Long id, EnumConfigEntity request) {
        EnumConfigEntity existing = getById(id);
        if (existing.getIsSystem()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置分类下的枚举不可修改");
        }
        request.setId(id);
        request.setCategory(existing.getCategory());
        request.setItemCode(existing.getItemCode());
        enumConfigMapper.update(request);
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public EnumConfigEntity deleteReturning(Long id) {
        EnumConfigEntity existing = getById(id);
        if (existing.getIsSystem()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置分类下的枚举不可删除");
        }
        enumConfigMapper.logicalDelete(id);
        return existing;
    }

    private Long resolveOrCreateCategoryId(String categoryCode) {
        Long id = enumCategoryMapper.selectIdByCategory(categoryCode);
        if (id != null) {
            return id;
        }
        EnumCategoryEntity row = new EnumCategoryEntity();
        row.setCategory(categoryCode);
        row.setCategoryName(categoryCode);
        row.setDescription(null);
        row.setIsSystem(false);
        row.setIsEnabled(true);
        row.setIsDeleted(false);
        enumCategoryMapper.insert(row);
        return row.getId();
    }
}
