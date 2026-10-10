package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ResourceTrafficPolicyEntity;

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

    int insert(ResourceTrafficPolicyEntity entity);

    int update(ResourceTrafficPolicyEntity entity);

    int deleteById(@Param("id") Long id);
}
