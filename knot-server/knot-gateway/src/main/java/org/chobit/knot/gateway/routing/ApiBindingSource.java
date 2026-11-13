package org.chobit.knot.gateway.routing;

import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;

import java.util.List;

/**
 * 模型 API 绑定数据源：路由候选按请求协议过滤时的唯一数据入口。
 * 网关运行时由 {@code GatewayConfigCacheService} 以方法引用适配（svc 查询 + 10 分钟缓存）。
 */
@FunctionalInterface
public interface ApiBindingSource {

    /**
     * @param modelId 模型主键
     * @return 该模型的全部 API 绑定（含禁用项），无绑定时返回空列表，不为 null
     */
    List<ModelApiBindingConfigVo> bindingsOf(Long modelId);
}
