package org.chobit.knot.gateway.upstream;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.model.ProxyResult;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Executes the public operation. Executes the public operation.
 */
public abstract class AbstractApiProtocolHandler implements ModelApiProtocolHandler {

    private final RestClient restClient;

    protected AbstractApiProtocolHandler(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public ProxyResult execute(UpstreamRequestContext context, UpstreamProviderAdapter adapter) {
        String path = adapter.resolvePath(context, defaultPath(context));
        if (path == null || path.isBlank()) {
            return new ProxyResult(null, context.provider().getId(), context.model().getId(),
                    ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code(), "api protocol is not configured: " + context.protocol().code());
        }

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(joinUrl(context.provider().getBaseUrl(), path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (context.traceparent() != null && !context.traceparent().isBlank()) {
                spec.header(GatewayHeaders.TRACEPARENT, context.traceparent().trim());
            }
            adapter.applyHeaders(spec, context);
            String responseBody = spec.body(adapter.buildRequestBody(context))
                    .retrieve()
                    .body(String.class);
            return new ProxyResult(
                    adapter.handleResponse(responseBody, context),
                    context.provider().getId(),
                    context.model().getId(),
                    null,
                    null
            );
        } catch (Exception e) {
            return new ProxyResult(null, context.provider().getId(), context.model().getId(),
                    ProxyErrorCodeEnum.UPSTREAM_ERROR.code(), e.getMessage());
        }
    }

    protected String defaultPath(UpstreamRequestContext context) {
        return context.protocol().defaultPath();
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        String p = path == null ? "" : path.trim();
        if (base.endsWith("/") && p.startsWith("/")) {
            return base.substring(0, base.length() - 1) + p;
        }
        if (!base.endsWith("/") && !p.startsWith("/")) {
            return base + "/" + p;
        }
        return base + p;
    }
}
