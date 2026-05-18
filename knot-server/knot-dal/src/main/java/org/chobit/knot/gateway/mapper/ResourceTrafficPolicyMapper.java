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

    int insert(ResourceTrafficPolicyEntity entity);

    int update(ResourceTrafficPolicyEntity entity);

    int deleteById(@Param("id") Long id);
}
