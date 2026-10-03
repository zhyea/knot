package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ProviderModelMappingEntity;

import java.util.List;

@Mapper
public interface LogicalModelMapper {

    List<LogicalModelEntity> list(@Param("keyword") String keyword,
                                  @Param("modelTypes") List<String> modelTypes);

    LogicalModelEntity getById(Long id);

    LogicalModelEntity getByCode(String modelCode);

    int insert(LogicalModelEntity entity);

    int update(LogicalModelEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int deleteById(Long id);

    Long countByModelCode(@Param("modelCode") String modelCode, @Param("excludeId") Long excludeId);

    List<ProviderModelMappingEntity> listMappings(String logicalModelCode);

    List<ProviderModelMappingEntity> listMappingsByModelId(Long modelId);

    ProviderModelMappingEntity getMappingById(Long id);

    int insertMapping(ProviderModelMappingEntity entity);

    int updateMapping(ProviderModelMappingEntity entity);

    int deleteMappingsByLogicalModelCode(String logicalModelCode);

    int deleteMappingsByModelId(Long modelId);

    int deleteMapping(@Param("logicalModelCode") String logicalModelCode, @Param("mappingId") Long mappingId);

    int logicalDelete(@Param("id") Long id);

    Long countMappingsByLogicalModelCode(@Param("logicalModelCode") String logicalModelCode);
}
