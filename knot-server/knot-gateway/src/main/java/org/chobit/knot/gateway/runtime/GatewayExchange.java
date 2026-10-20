package org.chobit.knot.gateway.runtime;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.chobit.knot.gateway.model.ProxyResult;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Accessors(fluent = true)
public class GatewayExchange {

    private final Map<String, Object> requestBody;
    private final MediaType contentType;
    /**
     * HTTP 读写入口：随请求一起显式传递，链路不再依赖 {@code RequestContextHolder}。
     */
    private final GatewayHttpExchange http;
    @Setter
    private ProxyResult proxyResult;

    /**
     * Constructs a new instance.
     */
    public GatewayExchange(Map<String, Object> requestBody) {
        this(requestBody, MediaType.APPLICATION_JSON);
    }

    /**
     * Constructs a new instance.
     */
    public GatewayExchange(Map<String, Object> requestBody, MediaType contentType) {
        this(requestBody, contentType, GatewayHttpExchange.noop());
    }

    /**
     * Constructs a new instance.
     */
    public GatewayExchange(Map<String, Object> requestBody, MediaType contentType, GatewayHttpExchange http) {
        this.requestBody = requestBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestBody);
        this.contentType = contentType == null ? MediaType.APPLICATION_JSON : contentType;
        this.http = http == null ? GatewayHttpExchange.noop() : http;
    }

}
