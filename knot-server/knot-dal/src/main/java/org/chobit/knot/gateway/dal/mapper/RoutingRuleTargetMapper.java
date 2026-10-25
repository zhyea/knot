package org.chobit.knot.gateway.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.dal.entity.RoutingRuleTargetEntity;

import java.util.List;

@Mapper
public interface RoutingRuleTargetMapper {

    List<RoutingRuleTargetEntity> listByRuleCodes(@Param("ruleCodes") List<String> ruleCodes);

    List<RoutingRuleTargetEntity> listByRuleCode(@Param("ruleCode") String ruleCode);

    int deleteByRuleCode(@Param("ruleCode") String ruleCode);

    int insert(RoutingRuleTargetEntity entity);
}
