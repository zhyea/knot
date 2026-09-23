package org.chobit.knot.gateway.constants.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelApiProtocolEnumTest {

    @Test
    void everyProtocolHasCodeAndDisplayName() {
        Arrays.stream(ModelApiProtocolEnum.values()).forEach(protocol -> {
            assertFalse(protocol.code().isBlank());
            assertFalse(protocol.displayName().isBlank());
        });
    }

    @Test
    void legacyAliasResolvesToCanonicalProtocol() {
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode("openai_chat_completions");

        assertEquals(ModelApiProtocolEnum.CHAT_COMPLETIONS, protocol.canonical());
    }
}
