package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.ResourceQuotaBindingEntity;

import java.util.List;

@Mapper
public interface ResourceQuotaBindingMapper {

    /**
     * 按资源类型 + 一组资源业务码加载绑定（读路径，loadBatch 用）。
     */
    List<ResourceQuotaBindingEntity> listByResources(@Param("resourceType") String resourceType,
                                                      @Param("resourceCodes") List<String> resourceCodes);

    /**
     * 整组替换：先删后插时调用，按资源类型 + 业务码批量写入绑定。
     */
    int insertBatch(@Param("bindings") List<ResourceQuotaBindingEntity> bindings);

    /**
     * 删除某资源下的全部额度绑定（写路径整组替换的前置步骤）。
     */
    int deleteByResource(@Param("resourceType") String resourceType,
                         @Param("resourceCode") String resourceCode);

    /**
     * 按资源类型 + 业务码统计额度绑定行数（改码守卫用，口径同
     * {@link ResourceTrafficPolicyMapper#countByResourceCode(String, String)}）。
     */
    Long countByResourceCode(@Param("resourceType") String resourceType,
                             @Param("resourceCode") String resourceCode);
}
