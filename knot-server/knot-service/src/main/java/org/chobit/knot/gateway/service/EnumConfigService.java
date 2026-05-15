package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.entity.EnumCategorySummary;
import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.EnumConfigMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EnumConfigService {

    private final EnumConfigMapper enumConfigMapper;

    public EnumConfigService(EnumConfigMapper enumConfigMapper) {
        this.enumConfigMapper = enumConfigMapper;
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
        // 检查同分类下 code 是否重复
        EnumConfigEntity existing = enumConfigMapper.getByCategoryAndCode(request.getCategory(), request.getItemCode());
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "枚举项 " + request.getCategory() + "/" + request.getItemCode() + " 已存在");
        }
        request.setIsSystem(false);
        if (request.getSortOrder() == null) request.setSortOrder(0);
        if (request.getIsEnabled() == null) request.setIsEnabled(true);
        enumConfigMapper.insert(request);
        return request;
    }

    @Transactional
    public EnumConfigEntity update(Long id, EnumConfigEntity request) {
        EnumConfigEntity existing = getById(id);
        if (existing.getIsSystem()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置枚举不可修改");
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统内置枚举不可删除");
        }
        enumConfigMapper.delete(id);
        return existing;
    }
}
