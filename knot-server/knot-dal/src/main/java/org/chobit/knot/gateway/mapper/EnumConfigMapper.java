package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EnumConfigMapper {

    List<EnumConfigEntity> list();

    List<EnumConfigEntity> listByCategoryFilter(@Param("category") String category);

    List<EnumConfigEntity> listByCategory(@Param("category") String category);

    List<String> listCategories();

    EnumConfigEntity getById(Long id);

    EnumConfigEntity getByCategoryAndCode(@Param("category") String category, @Param("itemCode") String itemCode);

    int insert(EnumConfigEntity entity);

    int update(EnumConfigEntity entity);

    int delete(Long id);
}
