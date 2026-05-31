package org.chobit.knot.gateway.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.GatewayRoutingInfo;
import org.chobit.knot.gateway.model.ResolvedRouting;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
@Accessors(fluent = true)
public class GatewayRequestContext {

    private final String authorization;
    private final String apiKey;
    private final String ruleCode;
    private final String traceparent;
    private final ModelApiProtocolEnum protocol;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    @Setter
    private ResolvedRouting routing;

    /**
     * Executes the public operation. Executes the public operation.
     */
    public GatewayRoutingInfo routingInfo() {
        return routing == null ? null : routing.routingInfo();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void putAttribute(String key, Object value) {
        if (key != null && !key.isBlank()) {
            attributes.put(key, value);
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }
}
