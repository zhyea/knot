package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;

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

    /** 引用了某个异步档案的绑定行数（档案删除前的占用检查） */
    int countByAsyncProfileCode(@Param("profileCode") String profileCode);

    /** 引用了某个异步档案的模型编码（最多 5 个，仅用于报错文案给出排查入口） */
    List<String> listModelCodesByAsyncProfileCode(@Param("profileCode") String profileCode);
}
