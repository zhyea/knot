package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.CodeCountRow;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;

import java.util.List;

@Mapper
public interface RoutingConsumerMapper {

    List<RoutingConsumerEntity> list(@Param("keyword") String keyword);

    RoutingConsumerEntity getById(Long id);

    RoutingConsumerEntity getBySecretKey(@Param("secretKey") String secretKey);

    /**
     * 按消费者业务码取消费者（含 {@code secret_key}）。返回的 {@code secretKey} 属凭据，
     * 严禁打进日志或下发给前端。
     */
    RoutingConsumerEntity getByCode(@Param("consumerCode") String consumerCode);

    Long countByConsumerCode(@Param("consumerCode") String consumerCode,
                             @Param("excludeId") Long excludeId);

    Long countRulesByConsumerCode(@Param("consumerCode") String consumerCode);

    /**
     * 批量版：一次取回多个消费者的绑定处数（路由规则 + 频控绑定 + 额度绑定），键为 {@code code}。
     * 列表页用它替代逐行 {@link #countRulesByConsumerCode(String)}。
     */
    List<CodeCountRow> countRulesByConsumerCodes(@Param("consumerCodes") List<String> consumerCodes);

    int insert(RoutingConsumerEntity entity);

    int update(RoutingConsumerEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);

    int updateSecretKey(@Param("id") Long id, @Param("secretKey") String secretKey);
}
