package org.chobit.knot.gateway.constants;

import java.util.Arrays;

/**
 * Gateway model API protocol definitions.
 */
public enum ModelApiProtocol {

    CHAT_COMPLETIONS("CHAT_COMPLETIONS", "/v1/chat/completions", true),
    RESPONSES("RESPONSES", "/v1/responses", true),
    MESSAGES("MESSAGES", "/v1/messages", true),
    COMPLETIONS("COMPLETIONS", "/v1/completions", true),
    EMBEDDINGS("EMBEDDINGS", "/v1/embeddings", false),
    IMAGE_GENERATIONS("IMAGE_GENERATIONS", "/v1/images/generations", false),
    IMAGE_EDITS("IMAGE_EDITS", "/v1/images/edits", false),
    AUDIO_TRANSCRIPTIONS("AUDIO_TRANSCRIPTIONS", "/v1/audio/transcriptions", false),
    AUDIO_TRANSLATIONS("AUDIO_TRANSLATIONS", "/v1/audio/translations", false),
    AUDIO_SPEECH("AUDIO_SPEECH", "/v1/audio/speech", true),
    VIDEO_GENERATIONS("VIDEO_GENERATIONS", "/v1/videos/generations", false),
    RERANK("RERANK", "/v1/rerank", false),
    MODERATIONS("MODERATIONS", "/v1/moderations", false),
    CUSTOM("CUSTOM", null, false),

    /** Backward-compatible aliases for existing seed data. */
    OPENAI_CHAT_COMPLETIONS("OPENAI_CHAT_COMPLETIONS", "/v1/chat/completions", true, CHAT_COMPLETIONS),
    OPENAI_COMPLETIONS("OPENAI_COMPLETIONS", "/v1/completions", true, COMPLETIONS),
    OPENAI_RESPONSES("OPENAI_RESPONSES", "/v1/responses", true, RESPONSES),
    ANTHROPIC_MESSAGES("ANTHROPIC_MESSAGES", "/v1/messages", true, MESSAGES),
    OTHER("OTHER", null, false, CUSTOM);

    private final String code;
    private final String defaultPath;
    private final boolean streamSupported;
    private final ModelApiProtocol canonical;

    ModelApiProtocol(String code, String defaultPath, boolean streamSupported) {
        this(code, defaultPath, streamSupported, null);
    }

    ModelApiProtocol(String code, String defaultPath, boolean streamSupported, ModelApiProtocol canonical) {
        this.code = code;
        this.defaultPath = defaultPath;
        this.streamSupported = streamSupported;
        this.canonical = canonical == null ? this : canonical;
    }

    public String code() {
        return code;
    }

    public String defaultPath() {
        return defaultPath;
    }

    public boolean streamSupported() {
        return streamSupported;
    }

    public ModelApiProtocol canonical() {
        return canonical;
    }

    public boolean matches(String protocolCode) {
        ModelApiProtocol protocol = fromCode(protocolCode);
        return protocol != null && protocol.canonical() == canonical();
    }

    public static ModelApiProtocol fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(protocol -> protocol.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
