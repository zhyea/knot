package org.chobit.knot.gateway.service.upstream;

import org.springframework.web.client.RestClient;

import java.util.Map;

public interface UpstreamProviderAdapter {

    boolean supports(String providerType);

    default String resolvePath(UpstreamRequestContext context, String defaultPath) {
        String bindingPath = context.binding() == null ? null : context.binding().getApiPath();
        return hasText(bindingPath) ? bindingPath.trim() : defaultPath;
    }

    Map<String, Object> buildRequestBody(UpstreamRequestContext context);

    void applyHeaders(RestClient.RequestBodySpec requestSpec, UpstreamRequestContext context);

    default String handleResponse(String responseBody, UpstreamRequestContext context) {
        return responseBody;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
