package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.GatewayRequestEntity;
import org.chobit.knot.gateway.mapper.GatewayRequestMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 请求日志记录服务
 */
@Service
public class RequestLogService {

    private final GatewayRequestMapper gatewayRequestMapper;

    public RequestLogService(GatewayRequestMapper gatewayRequestMapper) {
        this.gatewayRequestMapper = gatewayRequestMapper;
    }

    /**
     * 异步记录请求日志到 gateway_requests 表
     */
    @Async
    public void log(RateLimitService.AppContext appContext,
                    ProxyService.ProxyResult result,
                    long latencyMs) {
        GatewayRequestEntity entity = new GatewayRequestEntity();
        entity.setRequestId(UUID.randomUUID().toString());
        entity.setAppId(appContext.id());
        entity.setProviderId(result.providerId());
        entity.setModelId(result.modelId());
        entity.setBillingRuleId(result.billingRuleId());
        entity.setStatus(result.errorCode() == null ? "SUCCESS" : "FAILED");
        entity.setLatencyMs((int) latencyMs);
        entity.setInputTokens(0);   // TODO: 从响应中解析
        entity.setOutputTokens(0);  // TODO: 从响应中解析
        entity.setTotalTokens(0);   // TODO: 从响应中解析
        entity.setCostAmount(BigDecimal.ZERO);  // TODO: 从计费规则计算
        entity.setErrorCode(result.errorCode());
        entity.setRequestTime(LocalDateTime.now());

        gatewayRequestMapper.insert(entity);
    }
}
