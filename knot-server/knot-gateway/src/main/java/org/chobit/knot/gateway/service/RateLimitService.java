package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.EntityStatus;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.AppCredentialEntity;
import org.chobit.knot.gateway.mapper.AppCredentialMapper;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.constants.TrafficResourceType;
import org.chobit.knot.gateway.service.ResourceTrafficPolicySupport;
import org.springframework.stereotype.Service;

/**
 * 限流与配额校验服务
 */
@Service
public class RateLimitService {

    private final AppCredentialMapper appCredentialMapper;
    private final AppMapper appMapper;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    public RateLimitService(AppCredentialMapper appCredentialMapper,
                            AppMapper appMapper,
                            ResourceTrafficPolicySupport trafficPolicySupport) {
        this.appCredentialMapper = appCredentialMapper;
        this.appMapper = appMapper;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    /**
     * 通过 API Key 解析 App 上下文
     */
    public AppContext resolveApp(String apiKey) {
        AppCredentialEntity credential = appCredentialMapper.getByAppKey(apiKey);
        if (credential == null || !EntityStatus.ACTIVE.equals(credential.getStatus())) {
            return null;
        }
        AppEntity app = appMapper.getById(credential.getAppId());
        if (app == null || !EntityStatus.ENABLED.equals(app.getStatus())) {
            return null;
        }
        ResourceTrafficPolicySupport.TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceType.APP, app.getId());
        return new AppContext(
                app.getId(),
                app.getAppId(),
                app.getName(),
                traffic != null ? traffic.rateLimitPolicy() : null,
                traffic != null ? traffic.quotaPolicy() : null
        );
    }

    /**
     * 检查限流与配额
     * 当前为简化实现：仅检查配置是否存在，后续可接入 Redis 实现实时限流
     */
    public boolean checkRateLimit(AppContext appContext, String model) {
        if (appContext.rateLimitPolicy() != null) {
            // TODO: 接入 Redis 实现每秒/每分钟限流
            // RateLimitPolicy policy = appContext.rateLimitPolicy();
        }
        if (appContext.quotaPolicy() != null) {
            // TODO: 查询当日/当月用量，对比 quota 配置
            // QuotaPolicy quota = appContext.quotaPolicy();
        }
        return true;
    }

    /**
     * App 上下文信息
     */
    public record AppContext(Long id, String appId, String name,
                             RateLimitPolicy rateLimitPolicy,
                             QuotaPolicy quotaPolicy) {
    }
}
