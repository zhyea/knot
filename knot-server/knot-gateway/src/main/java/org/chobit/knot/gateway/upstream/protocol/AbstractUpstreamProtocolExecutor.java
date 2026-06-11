package org.chobit.knot.gateway.upstream.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;

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
    public ProxyResult execute(UpstreamRequestContext context, UpstreamRequestAdapter adapter) {
        String path = adapter.resolvePath(context, defaultPath(context));
        if (StringUtils.isBlank(path)) {
            throw new GatewayUpstreamException(
                    "api protocol is not configured: " + context.protocol().code(),
                    ProxyErrorCodeEnum.API_PROTOCOL_NOT_CONFIGURED.code()
            );
        }

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(joinUrl(context.baseUrl(), path))
                    .contentType(adapter.resolveContentType(context))
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM);
            if (StringUtils.isNotBlank(context.traceparent())) {
                spec.header(GatewayHeaders.TRACEPARENT, StringUtils.trim(context.traceparent()));
            }
            adapter.applyHeaders(spec, context);
            String responseBody = spec.body(buildRequestBody(context, adapter, adapter.resolveContentType(context)))
                    .retrieve()
                    .body(String.class);
            String handledResponseBody = adapter.handleResponse(responseBody, context);
            return new ProxyResult(
                    handledResponseBody,
                    context.provider().getId(),
                    context.model().getId(),
                    usageExtractorRegistry.extract(handledResponseBody, context, adapter)
            );
        } catch (RestClientResponseException e) {
            throw new GatewayUpstreamException(
                    e.getMessage(),
                    ProxyErrorCodeEnum.UPSTREAM_ERROR.code(),
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString()
            );
        } catch (Exception e) {
            throw new GatewayUpstreamException(e.getMessage(), ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
    }

    protected String defaultPath(UpstreamRequestContext context) {
        return context.protocol().defaultPath();
    }

    @SuppressWarnings("unchecked")
    private Object buildRequestBody(UpstreamRequestContext context, UpstreamRequestAdapter adapter, MediaType contentType) {
        Object body = adapter.buildRequestBody(context);
        if (!MediaType.MULTIPART_FORM_DATA.includes(contentType) || !(body instanceof Map<?, ?> map)) {
            return body;
        }
        return toMultipartBody((Map<String, Object>) map);
    }

    private MultiValueMap<String, Object> toMultipartBody(Map<String, Object> source) {
        MultiValueMap<String, Object> target = new LinkedMultiValueMap<>();
        source.forEach((key, value) -> addMultipartPart(target, key, value));
        return target;
    }

    private void addMultipartPart(MultiValueMap<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof MultipartFile file) {
            addMultipartFile(target, key, file);
            return;
        }
        if (value instanceof MultipartFile[] files) {
            for (MultipartFile file : files) {
                addMultipartFile(target, key, file);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addMultipartPart(target, key, item));
            return;
        }
        target.add(key, value);
    }

    private void addMultipartFile(MultiValueMap<String, Object> target, String key, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        Resource resource = file.getResource();
        target.add(key, resource);
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
