package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTypeEnumTest {

    @Test
    void containsThirteenTypesWithCodeAndDisplayName() {
        assertEquals(13, ModelTypeEnum.values().length);

        assertEquals("对话", ModelTypeEnum.CHAT.displayName());
        assertEquals("文本", ModelTypeEnum.TEXT.displayName());
        assertEquals("推理", ModelTypeEnum.REASONING.displayName());
        assertEquals("多模态", ModelTypeEnum.MULTIMODAL.displayName());
        assertEquals("向量", ModelTypeEnum.EMBEDDING.displayName());
        assertEquals("重排", ModelTypeEnum.RERANK.displayName());
        assertEquals("图像", ModelTypeEnum.IMAGE.displayName());
        assertEquals("语音", ModelTypeEnum.AUDIO.displayName());
        assertEquals("视频", ModelTypeEnum.VIDEO.displayName());
        assertEquals("文档理解", ModelTypeEnum.DOCUMENT.displayName());
        assertEquals("OCR", ModelTypeEnum.OCR.displayName());
        assertEquals("安全审核", ModelTypeEnum.MODERATION.displayName());
        assertEquals("工具辅助", ModelTypeEnum.UTILITY.displayName());

        for (ModelTypeEnum type : ModelTypeEnum.values()) {
            assertFalse(type.code().isBlank());
            assertFalse(type.displayName().isBlank());
            assertEquals(type.code(), type.code().trim().toUpperCase());
        }
    }

    @Test
    void sortedReturnsAllTypesBySortOrder() {
        List<ModelTypeEnum> sorted = ModelTypeEnum.sorted();

        assertEquals(13, sorted.size());
        assertEquals(List.of(
                ModelTypeEnum.CHAT,
                ModelTypeEnum.TEXT,
                ModelTypeEnum.REASONING,
                ModelTypeEnum.MULTIMODAL,
                ModelTypeEnum.EMBEDDING,
                ModelTypeEnum.RERANK,
                ModelTypeEnum.IMAGE,
                ModelTypeEnum.AUDIO,
                ModelTypeEnum.VIDEO,
                ModelTypeEnum.DOCUMENT,
                ModelTypeEnum.OCR,
                ModelTypeEnum.MODERATION,
                ModelTypeEnum.UTILITY
        ), sorted);
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(i + 1, sorted.get(i).sortOrder());
        }
    }

    @Test
    void conversationTypesShareConversationProtocols() {
        for (ModelTypeEnum type : List.of(ModelTypeEnum.CHAT, ModelTypeEnum.TEXT, ModelTypeEnum.REASONING)) {
            assertTrue(type.supportsProtocol("CHAT_COMPLETIONS"));
            assertTrue(type.supportsProtocol("RESPONSES"));
            assertTrue(type.supportsProtocol("MESSAGES"));
            assertTrue(type.supportsProtocol("COMPLETIONS"));
            assertTrue(type.supportsProtocol("OPENAI_CHAT_COMPLETIONS"));
            assertTrue(type.supportsProtocol("ANTHROPIC_MESSAGES"));
            assertFalse(type.supportsProtocol("EMBEDDINGS"));
            assertFalse(type.supportsProtocol("IMAGE_GENERATIONS"));
        }
    }

    @Test
    void multimodalSupportsConversationAndMediaProtocols() {
        ModelTypeEnum type = ModelTypeEnum.MULTIMODAL;

        assertTrue(type.supportsProtocol("CHAT_COMPLETIONS"));
        assertTrue(type.supportsProtocol("COMPLETIONS"));
        assertTrue(type.supportsProtocol("IMAGE_GENERATIONS"));
        assertTrue(type.supportsProtocol("IMAGE_EDITS"));
        assertTrue(type.supportsProtocol("IMAGE_VARIATIONS"));
        assertTrue(type.supportsProtocol("AUDIO_TRANSCRIPTIONS"));
        assertTrue(type.supportsProtocol("AUDIO_TRANSLATIONS"));
        assertTrue(type.supportsProtocol("AUDIO_SPEECH"));
        assertTrue(type.supportsProtocol("VIDEO_GENERATIONS"));
        assertFalse(type.supportsProtocol("EMBEDDINGS"));
        assertFalse(type.supportsProtocol("RERANK"));
    }

    @Test
    void capabilityTypesOnlySupportTheirOwnProtocols() {
        assertEquals(List.of("EMBEDDINGS", "CUSTOM"), ModelTypeEnum.EMBEDDING.supportedProtocolCodes());
        assertEquals(List.of("RERANK", "CUSTOM"), ModelTypeEnum.RERANK.supportedProtocolCodes());
        assertEquals(
                List.of("IMAGE_GENERATIONS", "IMAGE_EDITS", "IMAGE_VARIATIONS", "CUSTOM"),
                ModelTypeEnum.IMAGE.supportedProtocolCodes()
        );
        assertEquals(
                List.of("AUDIO_TRANSCRIPTIONS", "AUDIO_TRANSLATIONS", "AUDIO_SPEECH", "CUSTOM"),
                ModelTypeEnum.AUDIO.supportedProtocolCodes()
        );
        assertEquals(List.of("VIDEO_GENERATIONS", "CUSTOM"), ModelTypeEnum.VIDEO.supportedProtocolCodes());
        assertEquals(List.of("MODERATIONS", "CUSTOM"), ModelTypeEnum.MODERATION.supportedProtocolCodes());
        assertEquals(List.of("RERANK", "MODERATIONS", "CUSTOM"), ModelTypeEnum.UTILITY.supportedProtocolCodes());
        assertEquals(
                List.of("CHAT_COMPLETIONS", "RESPONSES", "MESSAGES", "CUSTOM"),
                ModelTypeEnum.DOCUMENT.supportedProtocolCodes()
        );
        assertEquals(ModelTypeEnum.DOCUMENT.supportedProtocolCodes(), ModelTypeEnum.OCR.supportedProtocolCodes());

        assertFalse(ModelTypeEnum.EMBEDDING.supportsProtocol("CHAT_COMPLETIONS"));
        assertFalse(ModelTypeEnum.RERANK.supportsProtocol("MODERATIONS"));
        assertFalse(ModelTypeEnum.IMAGE.supportsProtocol("VIDEO_GENERATIONS"));
    }

    @Test
    void customIsAvailableForEveryType() {
        for (ModelTypeEnum type : ModelTypeEnum.values()) {
            assertTrue(type.supportsProtocol("CUSTOM"), type.code());
            assertTrue(type.supportedProtocolCodes().contains("CUSTOM"), type.code());
        }
    }

    @Test
    void supportsProtocolIgnoresCaseBlanksAndUnknownValues() {
        assertTrue(ModelTypeEnum.EMBEDDING.supportsProtocol(" embeddings "));
        assertTrue(ModelTypeEnum.UTILITY.supportsProtocol("rerank"));
        assertFalse(ModelTypeEnum.UTILITY.supportsProtocol("NOT_A_PROTOCOL"));
        assertFalse(ModelTypeEnum.UTILITY.supportsProtocol(null));
        assertFalse(ModelTypeEnum.UTILITY.supportsProtocol("  "));
    }

    @Test
    void fromCodeToleratesCaseAndWhitespace() {
        assertSame(ModelTypeEnum.CHAT, ModelTypeEnum.fromCode(" chat "));
        assertSame(ModelTypeEnum.MULTIMODAL, ModelTypeEnum.fromCode("multimodal"));
        assertSame(ModelTypeEnum.OCR, ModelTypeEnum.fromCodeOrNull("oCr"));
    }

    @Test
    void fromCodeReturnsNullForBlankOrUnknown() {
        assertNull(ModelTypeEnum.fromCode(null));
        assertNull(ModelTypeEnum.fromCode("  "));
        assertNull(ModelTypeEnum.fromCode("NOT_A_TYPE"));
        assertNull(ModelTypeEnum.fromCodeOrNull("NOT_A_TYPE"));
    }

    @Test
    void canonicalProtocolsExcludeGenericProtocols() {
        for (ModelTypeEnum type : ModelTypeEnum.values()) {
            assertFalse(type.canonicalProtocols().contains(ModelApiProtocolEnum.CUSTOM), type.code());
            assertFalse(type.canonicalProtocols().isEmpty(), type.code());
        }
        assertEquals(
                Set.of(
                        ModelApiProtocolEnum.CHAT_COMPLETIONS,
                        ModelApiProtocolEnum.RESPONSES,
                        ModelApiProtocolEnum.MESSAGES,
                        ModelApiProtocolEnum.COMPLETIONS
                ),
                ModelTypeEnum.CHAT.canonicalProtocols()
        );
        assertEquals(
                Set.of(ModelApiProtocolEnum.RERANK, ModelApiProtocolEnum.MODERATIONS),
                ModelTypeEnum.UTILITY.canonicalProtocols()
        );
    }

    @Test
    void canonicalProtocolsOfFallsBackToChat() {
        assertEquals(ModelTypeEnum.CHAT.canonicalProtocols(), ModelTypeEnum.canonicalProtocolsOf("NOT_A_TYPE"));
        assertEquals(ModelTypeEnum.CHAT.canonicalProtocols(), ModelTypeEnum.canonicalProtocolsOf(null));
        assertEquals(ModelTypeEnum.VIDEO.canonicalProtocols(), ModelTypeEnum.canonicalProtocolsOf(" video "));
    }

    @Test
    void requireCodeReturnsCanonicalCodeOrFails() {
        assertEquals("EMBEDDING", ModelTypeEnum.requireCode("embedding", "unsupported model type"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ModelTypeEnum.requireCode("NOT_A_TYPE", "unsupported model type")
        );
        assertEquals(ErrorCode.VALIDATION_ERROR.code(), exception.getCode());
        assertEquals("unsupported model type", exception.getMessage());
    }
}
