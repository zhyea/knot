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
     * 按资源类型 + 业务码统计绑定行数。
     *
     * <p>改码守卫用：resource_code 存的是业务码，资源改码后这些行会指向不存在的 code（多态 join 落空），
     * 限流/额度会静默失效，所以计数时按 code 直接数，不 join 资源表（改码瞬间就已经 join 不上了）。</p>
     */
    Long countByResourceCode(@Param("resourceType") String resourceType,
                             @Param("resourceCode") String resourceCode);

    int insert(ResourceTrafficPolicyEntity entity);

    int update(ResourceTrafficPolicyEntity entity);

    int deleteById(@Param("id") Long id);
}
