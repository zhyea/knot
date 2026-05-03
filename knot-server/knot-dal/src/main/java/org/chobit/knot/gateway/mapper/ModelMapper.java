package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ModelMapper {

    List<ModelEntity> list();

    ModelEntity getById(Long id);

    int insert(ModelEntity entity);

    int update(ModelEntity entity);
}
