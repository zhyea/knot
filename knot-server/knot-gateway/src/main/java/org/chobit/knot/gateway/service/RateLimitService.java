package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.AppCredentialEntity;
import org.chobit.knot.gateway.mapper.AppCredentialMapper;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.converter.CommonMappings;
import org.springframework.stereotype.Service;

/**
 * 限流与配额校验服务
 */
@Service
public class RateLimitService {

    private final AppCredentialMapper appCredentialMapper;
    private final AppMapper appMapper;
    private final CommonMappings commonMappings;

    public RateLimitService(AppCredentialMapper appCredentialMapper,
                            AppMapper appMapper,
                            CommonMappings commonMappings) {
        this.appCredentialMapper = appCredentialMapper;
        this.appMapper = appMapper;
        this.commonMappings = commonMappings;
    }

    /**
     * 通过 API Key 解析 App 上下文
     */
    public AppContext resolveApp(String apiKey) {
        AppCredentialEntity credential = appCredentialMapper.getByAppKey(apiKey);
        if (credential == null || !"ACTIVE".equals(credential.getStatus())) {
            return null;
        }
        AppEntity app = appMapper.getById(credential.getAppId());
        if (app == null || !"ENABLED".equals(app.getStatus())) {
            return null;
        }
        return new AppContext(app.getId(), app.getAppId(), app.getName(),
                commonMappings.jsonToRateLimit(app.getRateLimitJson()),
                commonMappings.jsonToQuota(app.getQuotaJson()));
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
