package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.RateLimitPolicyEntity;

import java.util.List;

@Mapper
public interface RateLimitPolicyMapper {

    RateLimitPolicyEntity getById(@Param("id") Long id);

    List<RateLimitPolicyEntity> listByIds(@Param("ids") List<Long> ids);

    int insert(RateLimitPolicyEntity entity);

    int update(RateLimitPolicyEntity entity);

    int deleteById(@Param("id") Long id);
}
