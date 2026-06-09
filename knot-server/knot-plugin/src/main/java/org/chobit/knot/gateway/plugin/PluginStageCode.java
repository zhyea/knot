package org.chobit.knot.gateway.plugin;

public enum PluginStageCode {
    GATEWAY_REQUEST("GATEWAY_REQUEST"),
    GATEWAY_RESPONSE("GATEWAY_RESPONSE"),
    GATEWAY_ERROR("GATEWAY_ERROR"),
    UPSTREAM_REQUEST("UPSTREAM_REQUEST"),
    UPSTREAM_RESPONSE("UPSTREAM_RESPONSE"),
    UPSTREAM_ERROR("UPSTREAM_ERROR");

    private final String code;

    PluginStageCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PluginStageCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PluginStageCode value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
