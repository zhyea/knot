package org.chobit.knot.gateway.plugin;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class PluginDispatchContext {

    private final String traceId;
    private final String traceparent;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    protected PluginDispatchContext(String traceId, String traceparent) {
        this.traceId = traceId;
        this.traceparent = traceparent;
    }

    public String traceId() {
        return traceId;
    }

    public String traceparent() {
        return traceparent;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public void putAttribute(String key, Object value) {
        if (key != null && !key.isBlank()) {
            attributes.put(key, value);
        }
    }
}
