package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.ModelPoolItemEntity;

import java.util.List;

@Mapper
public interface ModelPoolMapper {

    List<ModelPoolEntity> list(@Param("keyword") String keyword, @Param("modelTypes") List<String> modelTypes);

    ModelPoolEntity getById(@Param("id") Long id);

    ModelPoolEntity getByCode(@Param("poolCode") String poolCode);

    Long countByPoolCode(@Param("poolCode") String poolCode, @Param("excludeId") Long excludeId);

    int insert(ModelPoolEntity entity);

    int update(ModelPoolEntity entity);

    int deleteById(@Param("id") Long id);

    List<ModelPoolItemEntity> listItemsByPoolId(@Param("poolId") Long poolId);

    int deleteItemsByPoolId(@Param("poolId") Long poolId);

    int insertItem(ModelPoolItemEntity entity);
}
