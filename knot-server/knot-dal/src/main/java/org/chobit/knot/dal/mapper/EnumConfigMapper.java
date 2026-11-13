package org.chobit.knot.dal.mapper;

import org.chobit.knot.dal.entity.EnumCategorySummary;
import org.chobit.knot.dal.entity.EnumConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EnumConfigMapper {

    List<EnumConfigEntity> list();

    List<EnumConfigEntity> listByCategoryFilter(@Param("category") String category);

    List<EnumConfigEntity> listByCategoryId(@Param("categoryId") Long categoryId);

    List<String> listCategories();

    List<EnumCategorySummary> listCategorySummaries(@Param("keyword") String keyword);

    EnumConfigEntity getById(Long id);

    EnumConfigEntity getByCategoryAndCode(@Param("category") String category, @Param("itemCode") String itemCode);

    /**
     * 改码守卫：统计按 item_code 引用该枚举项的行数。四类引用方（彼此互斥）：
     * 计费规则与统一模型的 {@code model_family}、折扣策略的 {@code discount_type}、
     * 敏感词条的 {@code category_code}。
     */
    Long countItemCodeBindings(@Param("itemCode") String itemCode);

    int insert(EnumConfigEntity entity);

    int update(EnumConfigEntity entity);

    int logicalDelete(Long id);
}
