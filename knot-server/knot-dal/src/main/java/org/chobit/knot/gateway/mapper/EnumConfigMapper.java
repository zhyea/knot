package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.EnumCategorySummary;
import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EnumConfigMapper {

    List<EnumConfigEntity> list();

    List<EnumConfigEntity> listByCategoryFilter(@Param("category") String category);

    List<EnumConfigEntity> listByCategoryId(@Param("categoryId") Long categoryId);

    List<String> listCategories();

    List<EnumCategorySummary> listCategorySummaries();

    EnumConfigEntity getById(Long id);

    EnumConfigEntity getByCategoryAndCode(@Param("category") String category, @Param("itemCode") String itemCode);

    int insert(EnumConfigEntity entity);

    int update(EnumConfigEntity entity);

    int delete(Long id);
}
