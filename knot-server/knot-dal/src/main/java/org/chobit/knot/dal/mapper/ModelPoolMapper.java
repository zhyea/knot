package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.CodeCountRow;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;

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

    /**
     * 批量版：一次取回多个池的条目，按 {@code pool_code} 分组消费。
     * 列表页用它替代逐池 {@link #listItemsByPoolCode(String)}。
     */
    List<ModelPoolItemEntity> listItemsByPoolCodes(@Param("poolCodes") List<String> poolCodes);

    int deleteItemsByPoolCode(@Param("poolCode") String poolCode);

    int insertItem(ModelPoolItemEntity entity);

    int logicalDelete(@Param("id") Long id);

    int restore(@Param("id") Long id);

    /** 引用检查按存储主键 code 统计（target_id 已于 10-06 迁移 DROP，仅剩 target_code） */
    Long countRoutingTargetsByPoolCode(@Param("poolCode") String poolCode);

    /**
     * 批量版：一次取回多个池被路由规则目标引用的处数，键为 {@code code}。
     * 列表页用它替代逐池 {@link #countRoutingTargetsByPoolCode(String)}。
     */
    List<CodeCountRow> countRoutingTargetsByPoolCodes(@Param("poolCodes") List<String> poolCodes);

    /**
     * 改码守卫：统计池内已挂载的条目数（{@code kb_model_pool_items} 无逻辑删除列，全部计入）。
     * 与列表展示的条目数口径不同：列表经 {@code kb_models} 内连接，已删模型的条目不计入。
     */
    Long countItemsByPoolCode(@Param("poolCode") String poolCode);
}
