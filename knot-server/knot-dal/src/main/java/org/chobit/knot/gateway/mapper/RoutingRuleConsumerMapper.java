package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.RoutingRuleConsumerEntity;

import java.util.List;

@Mapper
public interface RoutingRuleConsumerMapper {

    List<RoutingRuleConsumerEntity> listByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    void deleteByRuleId(Long ruleId);

    int insert(RoutingRuleConsumerEntity entity);
}
