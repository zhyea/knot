package org.chobit.knot.gateway.runtime;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.chobit.knot.gateway.model.ProxyResult;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Accessors(fluent = true)
public class GatewayExchange {

    private final Map<String, Object> requestBody;
    @Setter
    private ProxyResult proxyResult;

    /**
     * Constructs a new instance.
     */
    public GatewayExchange(Map<String, Object> requestBody) {
        this.requestBody = requestBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestBody);
    }

}
