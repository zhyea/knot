package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.RoutingRuleConsumerEntity;

import java.util.List;

@Mapper
public interface RoutingRuleConsumerMapper {

    List<RoutingRuleConsumerEntity> listByRuleCodes(@Param("ruleCodes") List<String> ruleCodes);

    void deleteByRuleCode(@Param("ruleCode") String ruleCode);

    int insert(RoutingRuleConsumerEntity entity);
}
