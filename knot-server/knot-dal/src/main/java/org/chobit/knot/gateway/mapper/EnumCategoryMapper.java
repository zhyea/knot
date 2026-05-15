package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.EnumCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EnumCategoryMapper {

    Long selectIdByCategory(@Param("category") String category);

    int insert(EnumCategoryEntity entity);
}
