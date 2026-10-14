package org.chobit.knot.gateway.routing;

import org.chobit.knot.gateway.entity.ModelApiBindingEntity;

import java.util.List;

/**
 * 模型 API 绑定数据源：路由候选按请求协议过滤时的唯一数据入口。
 *
 * <p>网关运行时由 {@code GatewayDataService} 以方法引用适配（库查询 + 10 分钟缓存）。
 * 抽函数接口而不是直接依赖 {@code GatewayDataService}，是为了让过滤逻辑可单测隔离——
 * 该类持有大量 Mapper 与缓存，无法在单测中构造（与 {@code TrafficPolicySource} 同一模式）。</p>
 */
@FunctionalInterface
public interface ApiBindingSource {

    /**
     * @param modelId 模型主键
     * @return 该模型的全部 API 绑定（含禁用项），无绑定时返回空列表，不为 null
     */
    List<ModelApiBindingEntity> bindingsOf(Long modelId);
}
