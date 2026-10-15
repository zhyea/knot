package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.vo.model.ModelTypeItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelServiceTest {

    @Test
    void listModelTypesReturnsEveryTypeOrderedBySortOrder() {
        List<ModelTypeItem> items = modelService().listModelTypes();

        assertEquals(14, items.size());
        assertEquals(List.of("CHAT", "TEXT", "REASONING", "MULTIMODAL", "EMBEDDING", "RERANK",
                "IMAGE", "AUDIO", "VIDEO", "DOCUMENT", "OCR", "MODERATION", "UTILITY", "DECISION"),
                items.stream().map(ModelTypeItem::code).toList());
        for (int i = 0; i < items.size(); i++) {
            assertEquals(i + 1, items.get(i).sortOrder());
            assertFalse(items.get(i).displayName().isBlank());
        }
    }

    @Test
    void listModelTypesExposesSupportedProtocolsOfEachType() {
        List<ModelTypeItem> items = modelService().listModelTypes();

        ModelTypeItem embedding = items.stream()
                .filter(item -> "EMBEDDING".equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("EMBEDDINGS", "CUSTOM"), embedding.supportedProtocols());

        ModelTypeItem multimodal = items.stream()
                .filter(item -> "MULTIMODAL".equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertTrue(multimodal.supportedProtocols().contains("CHAT_COMPLETIONS"));
        assertTrue(multimodal.supportedProtocols().contains("VIDEO_GENERATIONS"));

        // DECISIONS 协议经模型类型暴露给前端协议下拉，是新类型能被绑定的前提
        ModelTypeItem decision = items.stream()
                .filter(item -> "DECISION".equals(item.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("DECISIONS", "CUSTOM"), decision.supportedProtocols());

        items.forEach(item -> {
            assertTrue(item.supportedProtocols().contains("CUSTOM"), item.code());
        });
    }

    private static ModelService modelService() {
        return new ModelService(null, null, null, null, null, null, null, null, null, null);
    }
}
