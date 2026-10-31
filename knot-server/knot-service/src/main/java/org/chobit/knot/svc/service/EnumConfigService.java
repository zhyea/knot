package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.dal.entity.EnumCategoryEntity;
import org.chobit.knot.dal.entity.EnumConfigEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.EnumCategoryMapper;
import org.chobit.knot.dal.mapper.EnumConfigMapper;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.converter.EnumConfigConverter;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.vo.system.EnumCategorySummaryItem;
import org.chobit.knot.svc.vo.system.EnumConfigItem;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 枚举主数据服务。
 *
 * <p>⭐ 对外一律吐 {@link EnumConfigItem} / {@link EnumCategorySummaryItem}，实体只在私有方法里用。
 * 入参同样是 Item：{@code @OperationLog} 的 {@code oldValueSpel} 走
 * {@code @enumConfigService.getById}，新旧快照必须是同一种载体。</p>
 *
 * <p>⚠ {@link #listByCategory} 带 {@code @Cacheable}，缓存值是 Item 列表。
 * 缓存实现是 <b>Caffeine 本地缓存</b>（admin 的 {@code CacheConfiguration}），存在堆里、重启即空，
 * 所以改缓存值类型没有反序列化兼容问题；若哪天换成 Redis，这里要加缓存版本号。</p>
 */
@Service
public class EnumConfigService {

    /**
     * DB 枚举项缓存名（管理端 CacheConfiguration 以同名注册 Caffeine 缓存）。
     */
    public static final String ENUM_ITEMS_CACHE = "enumItems";

    /**
     * 已由后端代码枚举（GET /api/enum-options，EnumOptionRegistry）权威维护的 DB 分类编码。
     * 这些分类已在 data.sql 与迁移脚本 2026-10-02-retire-code-enums.sql 中退役，
     * 禁止在 DB 重新创建，否则会再次产生"双源漂移"。
     */
    private static final Set<String> CODE_ENUM_CATEGORY_CODES = Set.of(
            "app_type",
            "billing_unit",
            "billing_currency",
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
    private final EnumConfigConverter converter;

    /**
     * Constructs a new instance.
     */
    public EnumConfigService(EnumConfigMapper enumConfigMapper, EnumCategoryMapper enumCategoryMapper,
                             EnumConfigConverter converter) {
        this.enumConfigMapper = enumConfigMapper;
        this.enumCategoryMapper = enumCategoryMapper;
        this.converter = converter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<EnumConfigItem> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<EnumConfigItem> list(PageRequest pageRequest, String category) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<EnumConfigEntity> pageInfo = new PageInfo<>(
                    category != null && !category.isBlank()
                            ? enumConfigMapper.listByCategoryFilter(category)
                            : enumConfigMapper.list()
            );
            return PageResult.of(converter.toItemList(pageInfo.getList()), pageInfo.getTotal(),
                    pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * 按分类读取枚举项（缓存 {@link #ENUM_ITEMS_CACHE}，单参数方法默认 key 即 category）。
     *
     * <p>前端下拉改为每次打开强制回源后，这里用短 TTL 的本地缓存吸收重复请求、避免 DB 读放大；
     * 一致性由 create/update/delete 的整表失效兜底。</p>
     */
    @Cacheable(cacheNames = ENUM_ITEMS_CACHE)
    public List<EnumConfigItem> listByCategory(String category) {
        EnumCategoryEntity cat = enumCategoryMapper.selectByCategory(category);
        if (cat == null) {
            return List.of();
        }
        List<EnumConfigEntity> items = enumConfigMapper.listByCategoryId(cat.getId());
        for (EnumConfigEntity item : items) {
            item.setCategory(cat.getCategory());
            item.setIsSystem(cat.getIsSystem());
        }
        return converter.toItemList(items);
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
    public List<EnumCategorySummaryItem> listCategorySummaries(String keyword) {
        return converter.toSummaryItemList(enumConfigMapper.listCategorySummaries(StringUtils.trimToNull(keyword)));
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public EnumConfigItem getById(Long id) {
        return converter.toItem(requireEntity(id));
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @CacheEvict(cacheNames = ENUM_ITEMS_CACHE, allEntries = true)
    @Transactional
    public EnumConfigItem create(EnumConfigItem request) {
        if (request.category() == null || request.category().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类编码不能为空");
        }
        String categoryCode = request.category().trim();
        if (isReservedCategory(categoryCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "枚举分类 " + categoryCode + " 已退役（代码枚举接管或零消费孤儿），禁止在 DB 重建");
        }
        EnumConfigEntity existing = enumConfigMapper.getByCategoryAndCode(categoryCode, request.itemCode());
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "枚举项 " + categoryCode + "/" + request.itemCode() + " 已存在");
        }
        Long categoryId = resolveOrCreateCategoryId(categoryCode);
        // record 不可变：归一与补默认值都落在转出来的可变实体上，不去改入参
        EnumConfigEntity entity = converter.toEntity(request);
        entity.setCategoryId(categoryId);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getIsEnabled() == null) {
            entity.setIsEnabled(true);
        }
        enumConfigMapper.insert(entity);
        entity.setCategory(categoryCode);
        return converter.toItem(entity);
    }

    /**
     * 更新枚举项。编码口径与其他业务码一致：没有任何引用时可改，一旦被引用（计费规则/统一模型的
     * 模型族、折扣策略的折扣类型、敏感词条的分类）就拒绝改码——引用方存的都是 item_code，
     * 改码等于让存量行掉绑（模型族掉绑会直接落不到计费规则上）。名称/排序/启用/备注始终可改。
     */
    @CacheEvict(cacheNames = ENUM_ITEMS_CACHE, allEntries = true)
    @Transactional
    public EnumConfigItem update(Long id, EnumConfigItem request) {
        EnumConfigEntity existing = requireEntity(id);
        if (existing.getIsSystem()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置分类下的枚举不可修改");
        }
        String newCode = resolveItemCodeForUpdate(existing, request.itemCode());
        EnumConfigEntity entity = converter.toEntity(request);
        entity.setId(id);
        entity.setCategory(existing.getCategory());
        entity.setItemCode(newCode);
        enumConfigMapper.update(entity);
        return getById(id);
    }

    /**
     * 解析改码请求：空值表示「不改编码」（只改名称等字段），否则归一 + 唯一性 + 引用拦截。
     */
    private String resolveItemCodeForUpdate(EnumConfigEntity existing, String requestedCode) {
        String oldCode = CodeBindingSupport.normalize(existing.getItemCode());
        if (requestedCode == null || requestedCode.isBlank()) {
            return oldCode;
        }
        String newCode = CodeBindingSupport.normalize(requestedCode);
        if (CodeBindingSupport.isSameCode(oldCode, newCode)) {
            return oldCode;
        }
        EnumConfigEntity duplicated = enumConfigMapper.getByCategoryAndCode(existing.getCategory(), newCode);
        if (duplicated != null && !duplicated.getId().equals(existing.getId())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "枚举项 " + existing.getCategory() + "/" + newCode + " 已存在");
        }
        CodeBindingSupport.requireCodeChangeAllowed(oldCode, newCode,
                enumConfigMapper.countItemCodeBindings(oldCode), "枚举项编码");
        return newCode;
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @CacheEvict(cacheNames = ENUM_ITEMS_CACHE, allEntries = true)
    @Transactional
    public EnumConfigItem deleteReturning(Long id) {
        EnumConfigEntity existing = requireEntity(id);
        if (existing.getIsSystem()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置分类下的枚举不可删除");
        }
        enumConfigMapper.logicalDelete(id);
        return converter.toItem(existing);
    }

    /**
     * 内部取实体：对外走 {@link #getById}（吐 Item），需要实体做校验/拼装时才用这个。
     */
    private EnumConfigEntity requireEntity(Long id) {
        EnumConfigEntity entity = enumConfigMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "enum config not found");
        }
        return entity;
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
