package org.chobit.knot.gateway.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.dal.entity.ModelPoolEntity;
import org.chobit.knot.gateway.dal.entity.ModelPoolItemEntity;

import java.util.List;

@Mapper
public interface ModelPoolMapper {

    List<ModelPoolEntity> list(@Param("keyword") String keyword,
                               @Param("modelTypes") List<String> modelTypes,
                               @Param("includeDeleted") Boolean includeDeleted,
                               @Param("status") Integer status);

    ModelPoolEntity getById(@Param("id") Long id);

    ModelPoolEntity getByIdIncludingDeleted(@Param("id") Long id);

    ModelPoolEntity getByCode(@Param("poolCode") String poolCode);

    Long countByPoolCode(@Param("poolCode") String poolCode, @Param("excludeId") Long excludeId);

    int insert(ModelPoolEntity entity);

    int update(ModelPoolEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);

    int deleteById(@Param("id") Long id);

    List<ModelPoolItemEntity> listItemsByPoolCode(@Param("poolCode") String poolCode);

    int deleteItemsByPoolCode(@Param("poolCode") String poolCode);

    int insertItem(ModelPoolItemEntity entity);

    int logicalDelete(@Param("id") Long id);

    int restore(@Param("id") Long id);

    /** 引用检查按存储主键 code 统计（target_id 已于 10-06 迁移 DROP，仅剩 target_code） */
    Long countRoutingTargetsByPoolCode(@Param("poolCode") String poolCode);

    /**
     * 改码守卫：统计池内已挂载的供应商模型条目数（kb_model_pool_items 无逻辑删除列）。
     */
    Long countItemsByPoolCode(@Param("poolCode") String poolCode);
}
