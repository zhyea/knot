package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModelMapper {

    List<ModelEntity> list(@Param("keyword") String keyword, @Param("modelTypes") List<String> modelTypes);

    ModelEntity getById(Long id);

    int insert(ModelEntity entity);

    int update(ModelEntity entity);

    Long countByModelCode(@Param("modelCode") String modelCode,
                          @Param("excludeId") Long excludeId);
}
