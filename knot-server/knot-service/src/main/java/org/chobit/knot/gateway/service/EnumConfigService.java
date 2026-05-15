package org.chobit.knot.gateway.service;

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

@Service
public class EnumConfigService {

    private final EnumConfigMapper enumConfigMapper;
    private final EnumCategoryMapper enumCategoryMapper;

    public EnumConfigService(EnumConfigMapper enumConfigMapper, EnumCategoryMapper enumCategoryMapper) {
        this.enumConfigMapper = enumConfigMapper;
        this.enumCategoryMapper = enumCategoryMapper;
    }

    public PageResult<EnumConfigEntity> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    public PageResult<EnumConfigEntity> list(PageRequest pageRequest, String category) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<EnumConfigEntity> pageInfo = new PageInfo<>(
                category != null && !category.isBlank()
                        ? enumConfigMapper.listByCategoryFilter(category)
                        : enumConfigMapper.list()
        );
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    public List<EnumConfigEntity> listByCategory(String category) {
        return enumConfigMapper.listByCategory(category);
    }

    public List<String> listCategories() {
        return enumConfigMapper.listCategories();
    }

    public List<EnumCategorySummary> listCategorySummaries() {
        return enumConfigMapper.listCategorySummaries();
    }

    public EnumConfigEntity getById(Long id) {
        EnumConfigEntity entity = enumConfigMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "enum config not found");
        return entity;
    }

    @Transactional
    public EnumConfigEntity create(EnumConfigEntity request) {
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分类编码不能为空");
        }
        String categoryCode = request.getCategory().trim();
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

    @Transactional
    public EnumConfigEntity update(Long id, EnumConfigEntity request) {
        EnumConfigEntity existing = getById(id);
        if (existing.getIsSystem()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置分类下的枚举不可修改");
        }
        request.setId(id);
        request.setCategory(existing.getCategory());    // 不允许改 category
        request.setItemCode(existing.getItemCode());     // 不允许改 code
        enumConfigMapper.update(request);
        return getById(id);
    }

    @Transactional
    public EnumConfigEntity deleteReturning(Long id) {
        EnumConfigEntity existing = getById(id);
        if (existing.getIsSystem()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置分类下的枚举不可删除");
        }
        enumConfigMapper.delete(id);
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
        enumCategoryMapper.insert(row);
        return row.getId();
    }
}
