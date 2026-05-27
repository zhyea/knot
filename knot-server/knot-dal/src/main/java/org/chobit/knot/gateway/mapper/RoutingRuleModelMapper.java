package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.RoutingRuleModelEntity;

import java.util.List;

@Mapper
public interface RoutingRuleModelMapper {

    List<RoutingRuleModelEntity> listByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    List<RoutingRuleModelEntity> listByRuleId(@Param("ruleId") Long ruleId);

    int deleteByRuleId(@Param("ruleId") Long ruleId);

    int insert(RoutingRuleModelEntity entity);
}
