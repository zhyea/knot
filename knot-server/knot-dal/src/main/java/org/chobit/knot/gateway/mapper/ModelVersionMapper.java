package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ModelVersionEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ModelVersionMapper {

    List<ModelVersionEntity> listByModelId(Long modelId);

    ModelVersionEntity getActiveVersion(Long modelId);

    int insert(ModelVersionEntity entity);

    int updateStatus(ModelVersionEntity entity);
}
