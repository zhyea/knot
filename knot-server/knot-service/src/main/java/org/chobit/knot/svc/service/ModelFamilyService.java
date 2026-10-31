package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.dal.entity.ModelFamilyEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.ModelFamilyMapper;
import org.chobit.knot.svc.converter.ModelFamilyConverter;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.vo.model.ModelFamilyItem;
import org.chobit.knot.svc.vo.system.EnumConfigItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 模型族维护服务。
 *
 * <p>模型族是模型域概念，但主数据仍落在枚举表 {@code ks_enum_configs}
 * （category={@value #CATEGORY}），与「系统管理 / 枚举管理」同源；本服务只是把它按模型域
 * 单独暴露一套受权限控制的接口，避免为了维护模型族而开放全量枚举的读写权限。</p>
 *
 * <p>⭐ 对外只吐 {@link ModelFamilyItem}。实体仍在内部用（{@code EnumConfigEntity} 出现在
 * <b>私有逻辑</b>里是正常的）：红线管的是「跨模块出去的那一刻」，不是「svc 里能不能出现 entity」。</p>
 */
@Service
public class ModelFamilyService {

    /** 模型族在 ks_enum_categories 中的分类编码。 */
    public static final String CATEGORY = "model_family";

    /** 族编码字符集：小写字母/数字开头，允许 - 与 _；保持与 ModelFamilyResolver 的 contains 匹配一致。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]*$");

    private final ModelFamilyMapper modelFamilyMapper;
    private final EnumConfigService enumConfigService;
    private final ModelFamilyConverter converter;

    /**
     * Constructs a new instance.
     */
    public ModelFamilyService(ModelFamilyMapper modelFamilyMapper,
                              EnumConfigService enumConfigService,
                              ModelFamilyConverter converter) {
        this.modelFamilyMapper = modelFamilyMapper;
        this.enumConfigService = enumConfigService;
        this.converter = converter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<ModelFamilyItem> list(PageRequest pageRequest, String keyword) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<ModelFamilyEntity> pageInfo = new PageInfo<>(modelFamilyMapper.list(StringUtils.trimToNull(keyword)));
            return PageResult.of(converter.toItemList(pageInfo.getList()), pageInfo.getTotal(),
                    pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public ModelFamilyItem getById(Long id) {
        return converter.toItem(requireEntity(id));
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
    public ModelFamilyItem create(ModelFamilyItem request) {
        String code = normalizeCode(request.code());
        if (code == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型族编码不能为空");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "模型族编码只能由小写字母、数字、- 与 _ 组成，且以字母或数字开头");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型族名称不能为空");
        }

        // 只回填会被写入的字段；id / categoryId / isSystem / boundCount 由枚举服务侧决定
        EnumConfigItem created = enumConfigService.create(new EnumConfigItem(
                null, null, CATEGORY, code, name,
                request.sortOrder() == null ? 0 : request.sortOrder(),
                null, request.enabled() == null || request.enabled(), null,
                request.remark(), null));
        return getById(created.id());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public ModelFamilyItem update(Long id, ModelFamilyItem request) {
        ModelFamilyEntity existing = requireEntity(id);
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型族名称不能为空");
        }

        // itemCode 传 null = 「不改码」：模型族改码会让存量引用掉绑，这里刻意不支持
        enumConfigService.update(id, new EnumConfigItem(
                null, null, null, null, name,
                request.sortOrder() == null ? existing.getSortOrder() : request.sortOrder(),
                null, request.enabled() == null || request.enabled(), null,
                request.remark(), null));
        return getById(id);
    }

    /**
     * Updates the target resource status. Executes the public operation.
     */
    @Transactional
    public ModelFamilyItem updateStatus(Long id, boolean enabled) {
        ModelFamilyEntity existing = requireEntity(id);
        enumConfigService.update(id, new EnumConfigItem(
                null, null, null, null, existing.getName(), existing.getSortOrder(),
                null, enabled, null, existing.getRemark(), null));
        return getById(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @Transactional
    public void delete(Long id) {
        ModelFamilyEntity existing = requireEntity(id);
        int usage = existing.getUsageCount() == null ? 0 : existing.getUsageCount();
        if (usage > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "模型族 " + existing.getCode() + " 已被 " + usage + " 处引用（统一模型 / 计费规则），不可删除");
        }
        enumConfigService.deleteReturning(id);
    }

    /**
     * 内部取实体：对外一律走 {@link #getById}（吐 Item），需要实体做校验/拼装时才用这个。
     */
    private ModelFamilyEntity requireEntity(Long id) {
        ModelFamilyEntity entity = modelFamilyMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型族不存在");
        }
        return entity;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
