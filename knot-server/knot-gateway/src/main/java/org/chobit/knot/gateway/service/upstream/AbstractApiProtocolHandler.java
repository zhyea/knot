package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.service.ProxyService;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public abstract class AbstractApiProtocolHandler implements ModelApiProtocolHandler {

    private final RestClient restClient;

    protected AbstractApiProtocolHandler(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProxyService.ProxyResult execute(UpstreamRequestContext context, UpstreamProviderAdapter adapter) {
        String path = adapter.resolvePath(context, defaultPath(context));
        if (path == null || path.isBlank()) {
            return new ProxyService.ProxyResult(null, context.provider().getId(), context.model().getId(),
                    "API_PROTOCOL_NOT_CONFIGURED", "api protocol is not configured: " + context.protocol().code());
        }

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(joinUrl(context.provider().getBaseUrl(), path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON);
            if (context.traceparent() != null && !context.traceparent().isBlank()) {
                spec.header("traceparent", context.traceparent().trim());
            }
            adapter.applyHeaders(spec, context);
            String responseBody = spec.body(adapter.buildRequestBody(context))
                    .retrieve()
                    .body(String.class);
            return new ProxyService.ProxyResult(
                    adapter.handleResponse(responseBody, context),
                    context.provider().getId(),
                    context.model().getId(),
                    null,
                    null
            );
        } catch (Exception e) {
            return new ProxyService.ProxyResult(null, context.provider().getId(), context.model().getId(),
                    "UPSTREAM_ERROR", e.getMessage());
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
