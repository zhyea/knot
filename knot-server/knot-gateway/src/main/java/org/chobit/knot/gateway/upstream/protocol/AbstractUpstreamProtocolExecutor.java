package org.chobit.knot.gateway.upstream.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.chobit.knot.gateway.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * Executes the public operation. Executes the public operation.
 */
public abstract class AbstractUpstreamProtocolExecutor implements UpstreamProtocolExecutor {

    private final RestClient restClient;
    private final UsageExtractorRegistry usageExtractorRegistry;
    private final GatewayUpstreamClientProperties clientProperties;

    protected AbstractUpstreamProtocolExecutor(RestClient restClient,
                                               UsageExtractorRegistry usageExtractorRegistry,
                                               GatewayUpstreamClientProperties clientProperties) {
        this.restClient = restClient;
        this.usageExtractorRegistry = usageExtractorRegistry;
        this.clientProperties = clientProperties;
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
        MediaType contentType = adapter.resolveContentType(context);
        try {
            if (clientProperties.isStreamingEnabled() && supportsStreaming(context) && isStreamRequest(context)) {
                return executeStreaming(context, adapter, path, contentType);
            }
            return executeBuffered(context, adapter, path, contentType);
        } catch (GatewayUpstreamException e) {
            throw e;
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

    /**
     * 整包缓冲：把上游响应完整读成字符串后返回。适用于非流式请求。
     */
    private ProxyResult executeBuffered(UpstreamRequestContext context,
                                        UpstreamRequestAdapter adapter,
                                        String path,
                                        MediaType contentType) {
        RestClient.RequestBodySpec spec = requestSpec(context, adapter, path, contentType);
        String responseBody = spec.body(buildRequestBody(context, adapter, contentType))
                .retrieve()
                .body(String.class);
        String handledResponseBody = adapter.handleResponse(responseBody, context);
        return new ProxyResult(
                handledResponseBody,
                context.provider().getId(),
                context.model().getId(),
                usageExtractorRegistry.extract(handledResponseBody, context, adapter)
        );
    }

    /**
     * 流式转发：只读取响应头，响应体交给 {@link UpstreamStreamResponse} 边收边发。
     *
     * <p>首字节之前的状态码错误、连接超时、读超时都会在这里抛出，
     * 因此 failover 仍然有效；一旦开始转发则无法再切换目标。</p>
     */
    private ProxyResult executeStreaming(UpstreamRequestContext context,
                                         UpstreamRequestAdapter adapter,
                                         String path,
                                         MediaType contentType) {
        RestClient.RequestBodySpec spec = requestSpec(context, adapter, path, contentType);
        UpstreamStreamResponse streamResponse = spec.body(buildRequestBody(context, adapter, contentType))
                .exchange((request, response) -> toStreamResponse(context, adapter, response), false);
        return new ProxyResult(
                null,
                context.provider().getId(),
                context.model().getId(),
                null,
                streamResponse
        );
    }

    private UpstreamStreamResponse toStreamResponse(UpstreamRequestContext context,
                                                    UpstreamRequestAdapter adapter,
                                                    ClientHttpResponse response) throws IOException {
        HttpStatusCode statusCode = response.getStatusCode();
        if (!statusCode.is2xxSuccessful()) {
            String errorBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            throw new GatewayUpstreamException(
                    "upstream responded with status " + statusCode.value(),
                    ProxyErrorCodeEnum.UPSTREAM_ERROR.code(),
                    statusCode.value(),
                    errorBody
            );
        }
        return new UpstreamStreamResponse(
                response.getBody(),
                response.getHeaders().getContentType(),
                context,
                adapter,
                response
        );
    }

    /**
     * 当前协议是否支持流式转发。默认关闭，由文本生成类协议打开。
     */
    protected boolean supportsStreaming(UpstreamRequestContext context) {
        return false;
    }

    private boolean isStreamRequest(UpstreamRequestContext context) {
        Map<String, Object> body = context.requestBody();
        return body != null && Boolean.TRUE.equals(body.get(AiPayloadFields.STREAM));
    }

    private RestClient.RequestBodySpec requestSpec(UpstreamRequestContext context,
                                                   UpstreamRequestAdapter adapter,
                                                   String path,
                                                   MediaType contentType) {
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(joinUrl(context.baseUrl(), path))
                .contentType(contentType)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM);
        if (StringUtils.isNotBlank(context.traceparent())) {
            spec.header(GatewayHeaders.TRACEPARENT, StringUtils.trim(context.traceparent()));
        }
        adapter.applyHeaders(spec, context);
        return spec;
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
