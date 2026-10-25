package org.chobit.knot.dal.mapper;

import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoutingRuleMapper {

    List<RoutingRuleEntity> list(@Param("keyword") String keyword);

    RoutingRuleEntity getById(Long id);

    RoutingRuleEntity getEnabledByConsumerCodeAndRuleCode(@org.apache.ibatis.annotations.Param("consumerCode") String consumerCode,
                                                          @org.apache.ibatis.annotations.Param("ruleCode") String ruleCode);

    /**
     * 按规则业务码取规则（含 status）。
     *
     * <p>供裁判链路校验「裁判规则存在且启用」使用（v7 §6.1）。返回 null 表示规则不存在。</p>
     */
    RoutingRuleEntity getByRuleCode(@org.apache.ibatis.annotations.Param("ruleCode") String ruleCode);

    Long countByRuleCode(@org.apache.ibatis.annotations.Param("ruleCode") String ruleCode,
                         @org.apache.ibatis.annotations.Param("excludeId") Long excludeId);

    /**
     * 改码守卫：统计按应用业务码绑定该应用的路由规则数（kb_routing_rules 无逻辑删除列）。
     */
    Long countByAppCode(@Param("appCode") String appCode);

    /**
     * 改码守卫：统计按归属用户登录名绑定该用户的路由规则数。
     */
    Long countByUsername(@Param("username") String username);

    /**
     * 改码守卫：统计按统一模型编码关联该统一模型的路由规则数。
     */
    Long countByLogicalModelCode(@Param("logicalModelCode") String logicalModelCode);

    /**
     * 改码守卫：统计按规则业务码绑定的路由目标行数（kb_routing_rule_targets.rule_code）。
     */
    Long countTargetsByRuleCode(@Param("ruleCode") String ruleCode);

    int insert(RoutingRuleEntity entity);

    int update(RoutingRuleEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);
}
