package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnumOptionRegistryTest {

    private final EnumOptionRegistry registry = new EnumOptionRegistry();

    @Test
    void exposesOnlyMigratedEnums() {
        Map<String, Map<String, String>> map = registry.enumMap();

        assertEquals(2, map.size());
        assertTrue(map.containsKey("ModelTypeEnum"));
        assertTrue(map.containsKey("ModelApiProtocolEnum"));
    }

    @Test
    void everyOptionHasCodeAndLabel() {
        registry.enumMap().forEach((key, options) -> {
            assertFalse(options.isEmpty(), "empty options: " + key);
            options.forEach((code, label) -> {
                assertFalse(code.isBlank(), "blank code in " + key);
                assertFalse(label.isBlank(), "blank label for " + key + "." + code);
            });
        });
    }

    @Test
    void modelTypeEnumMatchesThirteenModelTypes() {
        Map<String, String> options = registry.enumMap().get("ModelTypeEnum");

        assertEquals(13, options.size());
        assertEquals("对话", options.get("CHAT"));
        assertEquals("工具辅助", options.get("UTILITY"));
    }

    @Test
    void returnedMapsAreImmutable() {
        Map<String, Map<String, String>> map = registry.enumMap();

        assertThrows(UnsupportedOperationException.class, () -> map.remove("ModelTypeEnum"));
        assertThrows(UnsupportedOperationException.class, () -> map.get("ModelTypeEnum").remove("CHAT"));
    }


    @Test
    void PrintSimpleName() {
        System.out.println(ModelTypeEnum.class.getSimpleName());
    }
}
