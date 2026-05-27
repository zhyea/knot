package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.RoutingHitLogEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RoutingRuleMapper {

    List<RoutingRuleEntity> list();

    RoutingRuleEntity getById(Long id);

    List<RoutingRuleEntity> listEnabledByConsumerId(Long consumerId);

    Long countByRuleCode(@org.apache.ibatis.annotations.Param("ruleCode") String ruleCode,
                         @org.apache.ibatis.annotations.Param("excludeId") Long excludeId);

    int insert(RoutingRuleEntity entity);

    int update(RoutingRuleEntity entity);

    List<RoutingHitLogEntity> listHitLogs();
}
