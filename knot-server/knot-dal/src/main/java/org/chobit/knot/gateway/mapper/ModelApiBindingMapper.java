package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;

import java.util.Collection;
import java.util.List;

public interface ModelApiBindingMapper {

    ModelApiBindingEntity getById(@Param("id") Long id);

    List<ModelApiBindingEntity> listByModelId(@Param("modelId") Long modelId);

    List<ModelApiBindingEntity> listByModelIds(@Param("modelIds") Collection<Long> modelIds);

    int insert(ModelApiBindingEntity entity);

    int update(ModelApiBindingEntity entity);

    int deleteById(@Param("id") Long id);

    int deleteByModelId(@Param("modelId") Long modelId);
}
