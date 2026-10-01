package org.chobit.knot.gateway.vo.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code CodeAvailability} 的序列化测试：确认替换 {@code Map.of("available", ...)} 之后，
 * 7 个 {@code /check-code} 端点对外输出的 JSON 形状没有变化。
 */
class CodeAvailabilitySerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldSerializeToSameShapeAsLegacyMap() throws Exception {
        assertEquals("{\"available\":true}", mapper.writeValueAsString(new CodeAvailability(true)));
        assertEquals("{\"available\":false}", mapper.writeValueAsString(new CodeAvailability(false)));
    }

    @Test
    void shouldMatchLegacyMapSerialization() throws Exception {
        assertEquals(mapper.writeValueAsString(java.util.Map.of("available", true)),
                mapper.writeValueAsString(new CodeAvailability(true)));
    }
}
