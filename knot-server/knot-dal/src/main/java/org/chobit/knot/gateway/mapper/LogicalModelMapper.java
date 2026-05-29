package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ProviderModelMappingEntity;

import java.util.List;

@Mapper
public interface LogicalModelMapper {

    List<LogicalModelEntity> list(@Param("keyword") String keyword);

    LogicalModelEntity getById(Long id);

    LogicalModelEntity getByCode(String modelCode);

    int insert(LogicalModelEntity entity);

    int update(LogicalModelEntity entity);

    int deleteById(Long id);

    Long countByModelCode(@Param("modelCode") String modelCode, @Param("excludeId") Long excludeId);

    List<ProviderModelMappingEntity> listMappings(Long logicalModelId);

    List<ProviderModelMappingEntity> listMappingsByModelId(Long modelId);

    ProviderModelMappingEntity getMappingById(Long id);

    int insertMapping(ProviderModelMappingEntity entity);

    int updateMapping(ProviderModelMappingEntity entity);

    int deleteMappingsByLogicalModelId(Long logicalModelId);

    int deleteMappingsByModelId(Long modelId);

    int deleteMapping(@Param("logicalModelId") Long logicalModelId, @Param("mappingId") Long mappingId);
}
