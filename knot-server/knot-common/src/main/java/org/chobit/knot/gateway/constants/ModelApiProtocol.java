package org.chobit.knot.gateway.constants;

/**
 * 供应商模型 API 协议类型。
 */
public enum ModelApiProtocol {

    /** OpenAI Chat Completions API */
    OPENAI_CHAT_COMPLETIONS("OPENAI_CHAT_COMPLETIONS", "/v1/chat/completions"),
    /** OpenAI Completions API（旧版文本补全） */
    OPENAI_COMPLETIONS("OPENAI_COMPLETIONS", "/v1/completions"),
    /** OpenAI Responses API */
    OPENAI_RESPONSES("OPENAI_RESPONSES", "/v1/responses"),
    /** Anthropic Messages API */
    ANTHROPIC_MESSAGES("ANTHROPIC_MESSAGES", "/v1/messages"),
    /** 其他自定义协议 */
    OTHER("OTHER", null);

    private final String code;
    private final String defaultPath;

    ModelApiProtocol(String code, String defaultPath) {
        this.code = code;
        this.defaultPath = defaultPath;
    }

    public String code() {
        return code;
    }

    public String defaultPath() {
        return defaultPath;
    }

    public static ModelApiProtocol fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (ModelApiProtocol protocol : values()) {
            if (protocol.code.equals(normalized)) {
                return protocol;
            }
        }
        return null;
    }
}
