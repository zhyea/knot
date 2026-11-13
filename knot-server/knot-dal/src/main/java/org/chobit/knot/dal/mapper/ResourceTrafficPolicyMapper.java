package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.ResourceTrafficPolicyEntity;

import java.util.List;

@Mapper
public interface ResourceTrafficPolicyMapper {

    ResourceTrafficPolicyEntity getByResource(@Param("resourceType") String resourceType,
                                              @Param("resourceId") Long resourceId);

    List<ResourceTrafficPolicyEntity> listByResources(@Param("resourceType") String resourceType,
                                                      @Param("resourceIds") List<Long> resourceIds);

    /**
     * 按资源类型把资源主键解析为业务码（多态：不同类型指向不同表的 code 列）。
     */
    String resolveResourceCode(@Param("resourceType") String resourceType,
                              @Param("resourceId") Long resourceId);

    /**
     * 按资源类型 + 业务码统计绑定行数（改码守卫用）。{@code resource_code} 存业务码，
     * 计数时按 code 直接数，不 join 资源表。
     */
    Long countByResourceCode(@Param("resourceType") String resourceType,
                             @Param("resourceCode") String resourceCode);

    int insert(ResourceTrafficPolicyEntity entity);

    int update(ResourceTrafficPolicyEntity entity);

    int deleteById(@Param("id") Long id);
}
