package org.chobit.knot.gateway.upstream.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.upstream.provider.UpstreamProviderAdapter;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Executes the public operation. Executes the public operation.
 */
public abstract class AbstractUpstreamProtocolExecutor implements UpstreamProtocolExecutor {

    private final RestClient restClient;
    private final UsageExtractorRegistry usageExtractorRegistry;

    protected AbstractUpstreamProtocolExecutor(RestClient restClient, UsageExtractorRegistry usageExtractorRegistry) {
        this.restClient = restClient;
        this.usageExtractorRegistry = usageExtractorRegistry;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public ProxyResult execute(UpstreamRequestContext context, UpstreamProviderAdapter adapter) {
        String path = adapter.resolvePath(context, defaultPath(context));
        if (StringUtils.isBlank(path)) {
            throw new GatewayUpstreamException(
                    "api protocol is not configured: " + context.protocol().code(),
                    ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code()
            );
        }

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(joinUrl(context.provider().getBaseUrl(), path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM);
            if (StringUtils.isNotBlank(context.traceparent())) {
                spec.header(GatewayHeaders.TRACEPARENT, StringUtils.trim(context.traceparent()));
            }
            adapter.applyHeaders(spec, context);
            String responseBody = spec.body(adapter.buildRequestBody(context))
                    .retrieve()
                    .body(String.class);
            String handledResponseBody = adapter.handleResponse(responseBody, context);
            return new ProxyResult(
                    handledResponseBody,
                    context.provider().getId(),
                    context.model().getId(),
                    usageExtractorRegistry.extract(handledResponseBody, context, adapter)
            );
        } catch (Exception e) {
            throw new GatewayUpstreamException(e.getMessage(), ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
    }

    protected String defaultPath(UpstreamRequestContext context) {
        return context.protocol().defaultPath();
    }

    private String joinUrl(String baseUrl, String path) {
        String base = StringUtils.trimToEmpty(baseUrl);
        String p = StringUtils.trimToEmpty(path);
        if (base.endsWith("/") && p.startsWith("/")) {
            return base.substring(0, base.length() - 1) + p;
        }
        if (!base.endsWith("/") && !p.startsWith("/")) {
            return base + "/" + p;
        }
        return base + p;
    }
}
