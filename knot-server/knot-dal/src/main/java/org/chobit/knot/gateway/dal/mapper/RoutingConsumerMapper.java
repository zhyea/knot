package org.chobit.knot.gateway.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.dal.entity.RoutingConsumerEntity;

import java.util.List;

@Mapper
public interface RoutingConsumerMapper {

    List<RoutingConsumerEntity> list(@Param("keyword") String keyword);

    RoutingConsumerEntity getById(Long id);

    RoutingConsumerEntity getBySecretKey(@Param("secretKey") String secretKey);

    /**
     * 按消费者业务码取消费者（含 secret_key）。
     *
     * <p>供「按 code 配置、调用时现取密钥」的链路使用，避免密钥在第二处落地。
     * 返回的 {@code secretKey} 属凭据，<b>严禁</b>打进日志或下发给前端。</p>
     */
    RoutingConsumerEntity getByCode(@Param("consumerCode") String consumerCode);

    Long countByConsumerCode(@Param("consumerCode") String consumerCode,
                             @Param("excludeId") Long excludeId);

    Long countRulesByConsumerCode(@Param("consumerCode") String consumerCode);

    int insert(RoutingConsumerEntity entity);

    int update(RoutingConsumerEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);

    int updateSecretKey(@Param("id") Long id, @Param("secretKey") String secretKey);
}
