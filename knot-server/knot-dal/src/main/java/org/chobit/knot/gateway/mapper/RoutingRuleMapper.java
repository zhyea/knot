package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.RoutingHitLogEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RoutingRuleMapper {
    @Select("select id,name,strategy_type,priority,condition_expr,target_provider_id,target_model_id,status from routing_rules order by priority asc, id desc")
    List<RoutingRuleEntity> list();

    @Select("select id,name,strategy_type,priority,condition_expr,target_provider_id,target_model_id,status from routing_rules where id=#{id}")
    RoutingRuleEntity getById(Long id);

    @Insert("insert into routing_rules(name,strategy_type,priority,condition_expr,target_provider_id,target_model_id,status) values(#{name},#{strategyType},#{priority},#{conditionExpr},#{targetProviderId},#{targetModelId},#{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RoutingRuleEntity entity);

    @Update("update routing_rules set name=#{name}, strategy_type=#{strategyType}, priority=#{priority}, condition_expr=#{conditionExpr}, target_provider_id=#{targetProviderId}, target_model_id=#{targetModelId}, status=#{status} where id=#{id}")
    int update(RoutingRuleEntity entity);

    @Select("select id, rule_id, from_target, to_target, reason, hit_time from routing_hit_logs order by id desc")
    List<RoutingHitLogEntity> listHitLogs();
}
