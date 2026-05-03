package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderEntity;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ProviderMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 请求代理转发服务
 */
@Service
public class ProxyService {

    private final RestClient restClient;
    private final ProviderMapper providerMapper;
    private final ModelMapper modelMapper;

    public ProxyService(RestClient restClient,
                        ProviderMapper providerMapper,
                        ModelMapper modelMapper) {
        this.restClient = restClient;
        this.providerMapper = providerMapper;
        this.modelMapper = modelMapper;
    }

    /**
     * 代理转发请求到目标 AI Provider
     *
     * @param requestBody 请求体
     * @param appContext  App 上下文
     * @return 代理结果
     */
    public ProxyResult proxy(Map<String, Object> requestBody, RateLimitService.AppContext appContext) {
        String modelCode = (String) requestBody.get("model");
        if (modelCode == null || modelCode.isBlank()) {
            return new ProxyResult(null, null, null, null, null, "MISSING_MODEL", "model is required");
        }

        // 查找 Model 配置
        ModelEntity model = findModelByCode(modelCode);
        if (model == null) {
            return new ProxyResult(null, null, null, null, null, "MODEL_NOT_FOUND",
                    "model not found: " + modelCode);
        }

        // 查找 Provider 配置
        ProviderEntity provider = providerMapper.getById(model.getProviderId());
        if (provider == null) {
            return new ProxyResult(null, null, model.getId(), model.getProviderId(), null,
                    "PROVIDER_NOT_FOUND", "provider not found for model: " + modelCode);
        }

        try {
            String targetUrl = provider.getBaseUrl() + "/v1/chat/completions";

            String responseBody = restClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return new ProxyResult(responseBody, provider.getId(), model.getId(),
                    provider.getId(), null, null, null);
        } catch (Exception e) {
            return new ProxyResult(null, provider.getId(), model.getId(),
                    provider.getId(), null, "UPSTREAM_ERROR", e.getMessage());
        }
    }

    private ModelEntity findModelByCode(String modelCode) {
        // 遍历所有 model 查找匹配的 modelCode
        // TODO: 后续可在 Mapper 中增加 getByModelCode 方法优化
        return modelMapper.list().stream()
                .filter(m -> modelCode.equals(m.getModelCode()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 代理转发结果
     */
    public record ProxyResult(String responseBody, Long providerId, Long modelId,
                              Long appId, Long billingRuleId,
                              String errorCode, String errorMessage) {
    }
}
