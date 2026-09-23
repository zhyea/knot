package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;

/**
 * Gateway model API protocol definitions.
 */
public enum ModelApiProtocolEnum {

    CHAT_COMPLETIONS("CHAT_COMPLETIONS", "Chat Completions", "/v1/chat/completions", true),
    RESPONSES("RESPONSES", "Responses", "/v1/responses", true),
    MESSAGES("MESSAGES", "Messages", "/v1/messages", true),
    COMPLETIONS("COMPLETIONS", "Completions", "/v1/completions", true),
    EMBEDDINGS("EMBEDDINGS", "Embeddings", "/v1/embeddings", false),
    IMAGE_GENERATIONS("IMAGE_GENERATIONS", "Image Generations", "/v1/images/generations", false),
    IMAGE_EDITS("IMAGE_EDITS", "Image Edits", "/v1/images/edits", false),
    IMAGE_VARIATIONS("IMAGE_VARIATIONS", "Image Variations", "/v1/images/variations", false),
    AUDIO_TRANSCRIPTIONS("AUDIO_TRANSCRIPTIONS", "Audio Transcriptions", "/v1/audio/transcriptions", false),
    AUDIO_TRANSLATIONS("AUDIO_TRANSLATIONS", "Audio Translations", "/v1/audio/translations", false),
    AUDIO_SPEECH("AUDIO_SPEECH", "Audio Speech", "/v1/audio/speech", true),
    VIDEO_GENERATIONS("VIDEO_GENERATIONS", "Video Generations", "/v1/videos/generations", false),
    RERANK("RERANK", "Rerank", "/v1/rerank", false),
    MODERATIONS("MODERATIONS", "Moderations", "/v1/moderations", false),
    CUSTOM("CUSTOM", "自定义", null, false),

    /** Backward-compatible aliases for existing seed data. */
    OPENAI_CHAT_COMPLETIONS("OPENAI_CHAT_COMPLETIONS", "OpenAI Chat Completions", "/v1/chat/completions", true, CHAT_COMPLETIONS),
    OPENAI_COMPLETIONS("OPENAI_COMPLETIONS", "OpenAI Completions", "/v1/completions", true, COMPLETIONS),
    OPENAI_RESPONSES("OPENAI_RESPONSES", "OpenAI Responses", "/v1/responses", true, RESPONSES),
    ANTHROPIC_MESSAGES("ANTHROPIC_MESSAGES", "Anthropic Messages", "/v1/messages", true, MESSAGES),
    OTHER("OTHER", "其他", null, false, CUSTOM);

    private final String code;
    private final String name;
    private final String defaultPath;
    private final boolean streamSupported;
    private final ModelApiProtocolEnum canonical;

    ModelApiProtocolEnum(String code, String name, String defaultPath, boolean streamSupported) {
        this(code, name, defaultPath, streamSupported, null);
    }

    ModelApiProtocolEnum(String code, String name, String defaultPath, boolean streamSupported,
                         ModelApiProtocolEnum canonical) {
        this.code = code;
        this.name = name;
        this.defaultPath = defaultPath;
        this.streamSupported = streamSupported;
        this.canonical = canonical == null ? this : canonical;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }

    public String displayName() {
        return name;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String defaultPath() {
        return defaultPath;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public boolean streamSupported() {
        return streamSupported;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public ModelApiProtocolEnum canonical() {
        return canonical;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public boolean matches(String protocolCode) {
        ModelApiProtocolEnum protocol = fromCode(protocolCode);
        return protocol != null && protocol.canonical() == canonical();
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static ModelApiProtocolEnum fromCode(String code) {
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
