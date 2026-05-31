package org.chobit.knot.gateway.service;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.entity.AppCredentialEntity;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.mapper.AppCredentialMapper;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.model.AppContext;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.springframework.stereotype.Service;

/**
 * 闄愭祦涓庨厤棰濇牎楠屾湇鍔? */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final AppCredentialMapper appCredentialMapper;
    private final AppMapper appMapper;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     */
    /**
     * 閫氳繃 API Key 瑙ｆ瀽 App 涓婁笅鏂?     */
    public AppContext resolveApp(String apiKey) {
        AppCredentialEntity credential = appCredentialMapper.getByAppKey(apiKey);
        if (credential == null || !EntityStatusEnum.ACTIVE.code().equals(credential.getStatus())) {
            return null;
        }
        AppEntity app = appMapper.getById(credential.getAppId());
        if (app == null || !EntityStatusEnum.ENABLED.code().equals(app.getStatus())) {
            return null;
        }
        TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceTypeEnum.APP.code(), app.getId());
        return new AppContext(
                app.getId(),
                app.getAppId(),
                app.getName(),
                traffic != null ? traffic.rateLimitPolicy() : null,
                traffic != null ? traffic.quotaPolicy() : null
        );
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    /**
     * 妫€鏌ラ檺娴佷笌閰嶉
     * 褰撳墠涓虹畝鍖栧疄鐜帮細浠呮鏌ラ厤缃槸鍚﹀瓨鍦紝鍚庣画鍙帴鍏?Redis 瀹炵幇瀹炴椂闄愭祦
     */
    public boolean checkRateLimit(AppContext appContext, String model) {
        if (appContext.rateLimitPolicy() != null) {
            // TODO: 鎺ュ叆 Redis 瀹炵幇姣忕/姣忓垎閽熼檺娴?            // RateLimitPolicy policy = appContext.rateLimitPolicy();
        }
        if (appContext.quotaPolicy() != null) {
            // TODO: 鏌ヨ褰撴棩/褰撴湀鐢ㄩ噺锛屽姣?quota 閰嶇疆
            // QuotaPolicy quota = appContext.quotaPolicy();
        }
        return true;
    }

    /**
     * App 涓婁笅鏂囦俊鎭?     */
}
