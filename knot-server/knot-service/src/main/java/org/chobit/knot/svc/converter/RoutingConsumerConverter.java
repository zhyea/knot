package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.routing.RoutingConsumerDto;
import org.chobit.knot.svc.vo.routing.RoutingConsumer;
import org.mapstruct.Mapper;

/**
 * 路由消费者 DTO ↔ 视图互转。
 *
 * <p>两侧字段一一同名同型（含 {@code rateLimitPolicy} / {@code quotaPolicies} 两个共享模型），
 * 所以是纯搬运，不需要任何 {@code @Mapping} 修正。存在的理由是让
 * {@code RoutingConsumerController} 只做「绑定入参 + 委托 + 视图映射」，
 * 而不是自己手写 11 个参数的构造（规范 {@code 10-backend-java.md §3.3}）。</p>
 */
@Mapper(componentModel = "spring")
public interface RoutingConsumerConverter {

    RoutingConsumer toVO(RoutingConsumerDto dto);

    RoutingConsumerDto toDto(RoutingConsumer vo);
}
