package org.chobit.knot.gateway.adapter.request;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.Set;

public interface UpstreamRequestAdapter {

    String code();

    String label();

    Set<ModelApiProtocolEnum> protocol();

    /**
     * 协议路径：优先绑定级 {@code api_path}，缺省走协议默认路径。
     *
     * <p>不处理模型级 {@code base_path}——那是 URL 前缀，由执行器统一拼接，
     * 避免每个适配器重复实现同样的拼接逻辑。</p>
     */
    default String resolvePath(UpstreamRequestContext context, String defaultPath) {
        String bindingPath = context.binding() == null ? null : context.binding().getApiPath();
        return StringUtils.defaultIfBlank(StringUtils.trim(bindingPath), defaultPath);
    }

    /**
     * 该适配器能否承接流式请求。默认 {@code true}；AWS Bedrock 的流式是 EventStream 二进制帧
     * （不是 SSE），P1 返回 {@code false} 让执行器走整包路径。
     */
    default boolean streamSupported(UpstreamRequestContext context) {
        return true;
    }

    Object buildRequestBody(UpstreamRequestContext context);

    default MediaType resolveContentType(UpstreamRequestContext context) {
        return context.contentType();
    }

    default String handleResponse(String responseBody, UpstreamRequestContext context) {
        return responseBody;
    }

    /**
     * 适配器兜底提取（提取器未命中或无用量时）：只认 token 口径的 usage，
     * 媒体计量由各媒体提取器负责。
     */
    default BillingUsage extractUsage(String responseBody, UpstreamRequestContext context) {
        if (StringUtils.isBlank(responseBody)) {
            return TokenBillingUsage.empty();
        }
        if (isEventStream(responseBody)) {
            return extractEventStreamUsage(responseBody, context);
        }
        Map<String, Object> body = readMap(responseBody);
        if (body == null || body.isEmpty()) {
            return TokenBillingUsage.empty();
        }
        return extractUsageFromBody(body);
    }

    default BillingUsage extractUsageFromBody(Map<String, Object> body) {
        return TokenBillingUsage.fromResponse(body);
    }

    default BillingUsage extractEventStreamUsage(String responseBody, UpstreamRequestContext context) {
        BillingUsage usage = TokenBillingUsage.empty();
        for (String line : responseBody.split("\\R")) {
            String trimmed = StringUtils.trim(line);
            if (!StringUtils.startsWith(trimmed, "data:")) {
                continue;
            }
            String data = StringUtils.trim(trimmed.substring("data:".length()));
            if (StringUtils.isEmpty(data) || "[DONE]".equals(data)) {
                continue;
            }
            Map<String, Object> event = readMap(data);
            if (event == null || event.isEmpty()) {
                continue;
            }
            usage = usage.mergeMax(extractUsageFromBody(event));
        }
        return usage;
    }

    private static boolean isEventStream(String value) {
        return value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    private static Map<String, Object> readMap(String json) {
        return JsonKit.fromJson(json, new TypeReference<>() {
        });
    }
}
