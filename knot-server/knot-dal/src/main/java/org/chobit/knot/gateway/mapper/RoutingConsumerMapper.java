package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;

import java.util.List;

@Mapper
public interface RoutingConsumerMapper {

    List<RoutingConsumerEntity> list();

    RoutingConsumerEntity getById(Long id);

    RoutingConsumerEntity getBySecretKey(@Param("secretKey") String secretKey);

    Long countByConsumerCode(@Param("consumerCode") String consumerCode,
                             @Param("excludeId") Long excludeId);

    Long countRulesByConsumerId(Long consumerId);

    int insert(RoutingConsumerEntity entity);

    int update(RoutingConsumerEntity entity);

    int updateSecretKey(@Param("id") Long id, @Param("secretKey") String secretKey);
}
