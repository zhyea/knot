package org.chobit.knot.gateway.dal.mapper;

import org.chobit.knot.gateway.dal.entity.EnumCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EnumCategoryMapper {

    Long selectIdByCategory(@Param("category") String category);

    EnumCategoryEntity selectByCategory(@Param("category") String category);

    int insert(EnumCategoryEntity entity);
}
