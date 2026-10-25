package org.chobit.knot.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextNormalizerTest {

    @Test
    void nullAndEmptyPassThrough() {
        assertNull(TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize(""));
    }

    @Test
    void lowercasesText() {
        assertEquals("abc", TextNormalizer.normalize("ABC"));
    }

    @Test
    void stripsZeroWidthAndFormatCharacters() {
        assertEquals("敏感词", TextNormalizer.normalize("敏\u200B感\u200D词"));
    }

    @Test
    void appliesNfkcForFullWidthForms() {
        assertEquals("abc123", TextNormalizer.normalize("ＡＢＣ１２３"));
    }

    @Test
    void keepsMeaningfulSpacing() {
        assertEquals("a b", TextNormalizer.normalize("A B"));
    }
}
