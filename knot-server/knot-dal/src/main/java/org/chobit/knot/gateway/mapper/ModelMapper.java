package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.ModelEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ModelMapper {

    List<ModelEntity> list(@Param("keyword") String keyword,
                           @Param("modelTypes") List<String> modelTypes,
                           @Param("logicalModelCode") String logicalModelCode,
                           @Param("status") Integer status,
                           @Param("includeDeleted") Boolean includeDeleted);

    ModelEntity getById(Long id);

    /** 含已删除：供「恢复」前的存在性判断与管理端已删除行回显使用 */
    ModelEntity getByIdIncludingDeleted(@Param("id") Long id);

    ModelEntity getByCode(@Param("modelCode") String modelCode);

    int insert(ModelEntity entity);

    int update(ModelEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);

    int logicalDelete(@Param("id") Long id);

    int restore(@Param("id") Long id);

    /** 引用检查按存储主键 code 统计（target_id 已于 10-06迁移 DROP，仅剩 target_code） */
    Long countRoutingTargetsByModelCode(@Param("modelCode") String modelCode);

    /** 引用检查：模型池条目（kb_model_pool_items.model_code），逻辑删除会使池内条目查询 inner join 落空 */
    Long countPoolItemsByModelCode(@Param("modelCode") String modelCode);

    /** 引用检查：厂商模型映射（kb_provider_model_mappings.model_id） */
    Long countProviderMappingsByModelId(@Param("modelId") Long modelId);

    /** 引用检查：模型 API 协议绑定（kb_model_api_bindings.model_id） */
    Long countApiBindingsByModelId(@Param("modelId") Long modelId);

    Long countByModelCode(@Param("modelCode") String modelCode,
                          @Param("excludeId") Long excludeId);
}
