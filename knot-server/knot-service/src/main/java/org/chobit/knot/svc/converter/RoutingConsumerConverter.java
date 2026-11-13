package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.routing.RoutingConsumerDto;
import org.chobit.knot.svc.vo.routing.RoutingConsumer;
import org.mapstruct.Mapper;

/**
 * 路由消费者 DTO ↔ 视图互转。
 *
 * 两侧字段一一同名同型（含 {@code rateLimitPolicy} / {@code quotaPolicies} 两个共享模型），纯搬运，无 {@code @Mapping}。
 */
@Mapper(componentModel = "spring")
public interface RoutingConsumerConverter {

    RoutingConsumer toVO(RoutingConsumerDto dto);

    RoutingConsumerDto toDto(RoutingConsumer vo);
}
