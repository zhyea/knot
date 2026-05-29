package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.RoutingRuleTargetEntity;

import java.util.List;

@Mapper
public interface RoutingRuleTargetMapper {

    List<RoutingRuleTargetEntity> listByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    List<RoutingRuleTargetEntity> listByRuleId(@Param("ruleId") Long ruleId);

    int deleteByRuleId(@Param("ruleId") Long ruleId);

    int insert(RoutingRuleTargetEntity entity);
}
