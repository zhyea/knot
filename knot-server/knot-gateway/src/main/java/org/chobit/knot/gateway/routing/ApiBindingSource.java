package org.chobit.knot.gateway.routing;

import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;

import java.util.List;

/**
 * 模型 API 绑定数据源：路由候选按请求协议过滤时的唯一数据入口。
 *
 * <p>网关运行时由 {@code GatewayConfigCacheService} 以方法引用适配（svc 查询 + 10 分钟缓存）。
 * 抽函数接口而不是直接依赖 {@code GatewayConfigCacheService}，是为了让过滤逻辑可单测隔离——
 * 后者依赖 {@code GatewayConfigQueryService}，而那个要 11 个 Mapper 才构造得出来
 * （它自己不持有 Mapper，这一点与 {@code TrafficPolicySource} 同一模式：缓存壳好造，数据源不好造）。</p>
 */
@FunctionalInterface
public interface ApiBindingSource {

    /**
     * @param modelId 模型主键
     * @return 该模型的全部 API 绑定（含禁用项），无绑定时返回空列表，不为 null
     */
    List<ModelApiBindingConfigVo> bindingsOf(Long modelId);
}
