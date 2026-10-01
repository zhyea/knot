package org.chobit.knot.gateway.plugin;

/**
 * 插件事件类型：eventSink 发出的 {@link PluginEvent} 分类。
 * code 为事件对外呈现的标识（日志 / Kafka 占位输出），保持小写连字符风格，勿改动既有取值。
 */
public enum PluginEventType {
    GATEWAY_REQUEST("gateway-request"),
    GATEWAY_RESPONSE("gateway-response"),
    GATEWAY_ERROR("gateway-error"),
    PROVIDER_REQUEST("provider-request"),
    PROVIDER_RESPONSE("provider-response"),
    PROVIDER_ERROR("provider-error");

    private final String code;

    PluginEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PluginEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PluginEventType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
