package org.chobit.knot.gateway.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.dal.entity.LogicalModelEntity;
import org.chobit.knot.gateway.dal.entity.ProviderModelMappingEntity;

import java.util.List;

@Mapper
public interface LogicalModelMapper {

    List<LogicalModelEntity> list(@Param("keyword") String keyword,
                                  @Param("modelTypes") List<String> modelTypes,
                                  @Param("includeDeleted") Boolean includeDeleted);

    LogicalModelEntity getById(Long id);

    LogicalModelEntity getByIdIncludingDeleted(Long id);

    LogicalModelEntity getByCode(String modelCode);

    int insert(LogicalModelEntity entity);

    int update(LogicalModelEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

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

    int restore(@Param("id") Long id);

    Long countMappingsByLogicalModelCode(@Param("logicalModelCode") String logicalModelCode);

    /**
     * 改码守卫：统计统一模型编码被外部按业务码引用的总处数。
     *
     * <p>四类引用：模型池归属、供应商模型映射、路由规则关联、调试预设归类。
     * 前两类表可逻辑删除且可恢复（kb_model_pools），故不按 is_deleted 过滤。</p>
     */
    Long countBindingsByModelCode(@Param("modelCode") String modelCode);
}
