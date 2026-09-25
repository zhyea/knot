package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gateway model type definitions.
 *
 * <p>The type list and the "model type -> allowed API protocols" rule are maintained here only.
 * The database enum category {@code model_type} is retired, callers must not read the display
 * name from {@code ks_enum_configs}.</p>
 */
public enum ModelTypeEnum implements EnumOption {

    CHAT("CHAT", "对话", 1,
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_CHAT_COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_RESPONSES,
            ModelApiProtocolEnum.ANTHROPIC_MESSAGES,
            ModelApiProtocolEnum.OPENAI_COMPLETIONS),

    TEXT("TEXT", "文本", 2,
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_CHAT_COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_RESPONSES,
            ModelApiProtocolEnum.ANTHROPIC_MESSAGES,
            ModelApiProtocolEnum.OPENAI_COMPLETIONS),

    REASONING("REASONING", "推理", 3,
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_CHAT_COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_RESPONSES,
            ModelApiProtocolEnum.ANTHROPIC_MESSAGES,
            ModelApiProtocolEnum.OPENAI_COMPLETIONS),

    MULTIMODAL("MULTIMODAL", "多模态", 4,
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_CHAT_COMPLETIONS,
            ModelApiProtocolEnum.OPENAI_RESPONSES,
            ModelApiProtocolEnum.ANTHROPIC_MESSAGES,
            ModelApiProtocolEnum.OPENAI_COMPLETIONS,
            ModelApiProtocolEnum.IMAGE_GENERATIONS,
            ModelApiProtocolEnum.IMAGE_EDITS,
            ModelApiProtocolEnum.IMAGE_VARIATIONS,
            ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
            ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
            ModelApiProtocolEnum.AUDIO_SPEECH,
            ModelApiProtocolEnum.VIDEO_GENERATIONS),

    EMBEDDING("EMBEDDING", "向量", 5,
            ModelApiProtocolEnum.EMBEDDINGS),

    RERANK("RERANK", "重排", 6,
            ModelApiProtocolEnum.RERANK),

    IMAGE("IMAGE", "图像", 7,
            ModelApiProtocolEnum.IMAGE_GENERATIONS,
            ModelApiProtocolEnum.IMAGE_EDITS,
            ModelApiProtocolEnum.IMAGE_VARIATIONS),

    AUDIO("AUDIO", "语音", 8,
            ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
            ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
            ModelApiProtocolEnum.AUDIO_SPEECH),

    VIDEO("VIDEO", "视频", 9,
            ModelApiProtocolEnum.VIDEO_GENERATIONS),

    DOCUMENT("DOCUMENT", "文档理解", 10,
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES),

    OCR("OCR", "OCR", 11,
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES),

    MODERATION("MODERATION", "安全审核", 12,
            ModelApiProtocolEnum.MODERATIONS),

    UTILITY("UTILITY", "工具辅助", 13,
            ModelApiProtocolEnum.RERANK,
            ModelApiProtocolEnum.MODERATIONS);

    private final String code;
    private final String displayName;
    private final int sortOrder;
    private final List<ModelApiProtocolEnum> supportedProtocols;
    private final Set<ModelApiProtocolEnum> canonicalProtocols;

    ModelTypeEnum(String code, String displayName, int sortOrder, ModelApiProtocolEnum... protocols) {
        this.code = code;
        this.displayName = displayName;
        this.sortOrder = sortOrder;

        List<ModelApiProtocolEnum> declared = Arrays.asList(protocols);
        List<ModelApiProtocolEnum> supported = new ArrayList<>(declared);
        supported.add(ModelApiProtocolEnum.CUSTOM);
        supported.add(ModelApiProtocolEnum.OTHER);

        Set<ModelApiProtocolEnum> canonicals = new LinkedHashSet<>();
        declared.forEach(protocol -> canonicals.add(protocol.canonical()));

        this.supportedProtocols = Collections.unmodifiableList(supported);
        this.canonicalProtocols = Collections.unmodifiableSet(canonicals);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public String label() {
        return displayName;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public int sortOrder() {
        return sortOrder;
    }

    /**
     * Returns protocols allowed by this model type, including the generic CUSTOM/OTHER ones.
     */
    public List<ModelApiProtocolEnum> supportedProtocols() {
        return supportedProtocols;
    }

    /**
     * Returns protocol codes allowed by this model type, including the generic CUSTOM/OTHER ones.
     */
    public List<String> supportedProtocolCodes() {
        return supportedProtocols.stream().map(ModelApiProtocolEnum::code).toList();
    }

    /**
     * Returns canonical protocols allowed by this model type, used when no binding is configured.
     */
    public Set<ModelApiProtocolEnum> canonicalProtocols() {
        return canonicalProtocols;
    }

    /**
     * Returns whether the given protocol code is allowed by this model type.
     */
    public boolean supportsProtocol(String protocolCode) {
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(protocolCode);
        if (protocol == null) {
            return false;
        }
        if (isGenericProtocol(protocol)) {
            return true;
        }
        return canonicalProtocols.contains(protocol.canonical());
    }

    /**
     * Resolves the model type by code, tolerating blanks and letter case.
     */
    public static ModelTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves the model type by code, returning null when the code is unknown.
     */
    public static ModelTypeEnum fromCodeOrNull(String code) {
        return fromCode(code);
    }

    /**
     * Returns all model types ordered by {@link #sortOrder()}.
     */
    public static List<ModelTypeEnum> sorted() {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(ModelTypeEnum::sortOrder))
                .toList();
    }

    /**
     * Returns canonical protocols for the given model type code, falling back to CHAT when unknown.
     */
    public static Set<ModelApiProtocolEnum> canonicalProtocolsOf(String modelType) {
        ModelTypeEnum type = fromCode(modelType);
        return type == null ? CHAT.canonicalProtocols() : type.canonicalProtocols();
    }

    /**
     * Validates the model type code and returns its canonical code.
     */
    public static String requireCode(String modelType, String errorMessage) {
        ModelTypeEnum type = fromCode(modelType);
        if (type == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return type.code();
    }

    private static boolean isGenericProtocol(ModelApiProtocolEnum protocol) {
        return ModelApiProtocolEnum.CUSTOM == protocol.canonical();
    }
}
