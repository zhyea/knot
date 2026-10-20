package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoutingRuleMapper {

    List<RoutingRuleEntity> list(@Param("keyword") String keyword);

    RoutingRuleEntity getById(Long id);

    List<RoutingRuleEntity> listEnabledByConsumerCode(@org.apache.ibatis.annotations.Param("consumerCode") String consumerCode);

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

    int insert(RoutingRuleEntity entity);

    int update(RoutingRuleEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);
}
