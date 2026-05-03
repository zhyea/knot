package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.RoutingHitLogEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RoutingRuleMapper {

    List<RoutingRuleEntity> list();

    RoutingRuleEntity getById(Long id);

    int insert(RoutingRuleEntity entity);

    int update(RoutingRuleEntity entity);

    List<RoutingHitLogEntity> listHitLogs();
}
