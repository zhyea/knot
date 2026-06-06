package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoutingRuleMapper {

    List<RoutingRuleEntity> list(@Param("keyword") String keyword,
                                 @Param("modelTypes") List<String> modelTypes);

    RoutingRuleEntity getById(Long id);

    List<RoutingRuleEntity> listEnabledByConsumerId(Long consumerId);

    RoutingRuleEntity getEnabledByConsumerIdAndRuleCode(@org.apache.ibatis.annotations.Param("consumerId") Long consumerId,
                                                        @org.apache.ibatis.annotations.Param("ruleCode") String ruleCode);

    Long countByRuleCode(@org.apache.ibatis.annotations.Param("ruleCode") String ruleCode,
                         @org.apache.ibatis.annotations.Param("excludeId") Long excludeId);

    int insert(RoutingRuleEntity entity);

    int update(RoutingRuleEntity entity);
}
