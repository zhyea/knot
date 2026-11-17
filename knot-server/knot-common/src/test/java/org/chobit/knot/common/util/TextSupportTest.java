package org.chobit.knot.common.util;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 契约测试：空白归一与必填校验的口径。
 *
 * <p>⚠ 全项目只有这一份判据，所以这里钉的是「哪些输入算缺失」：Unicode 空白也算
 * （全角空格、不换行空格、制表符、换行），只清洗首尾、不动中间。</p>
 */
class TextSupportTest {

    @Test
    @DisplayName("require：去首尾空白后原样返回")
    void requireTrimsSurroundingWhitespace() {
        assertEquals("abc", TextSupport.require("  abc  ", "model name is required"));
        assertEquals("a b", TextSupport.require("\ta b\n", "x"));
    }

    @Test
    @DisplayName("require：null 抛 VALIDATION_ERROR，文案用调用方给的那句")
    void requireRejectsNullWithCallerMessage() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TextSupport.require(null, "model name is required"));
        assertEquals(ErrorCode.VALIDATION_ERROR.code(), ex.getCode());
        assertEquals("model name is required", ex.getMessage());
    }

    @Test
    @DisplayName("require：全空白（含全角空格）一律拒绝")
    void requireRejectsBlankText() {
        assertThrows(BusinessException.class, () -> TextSupport.require("   ", "x"));
        assertThrows(BusinessException.class, () -> TextSupport.require("\t\n", "x"));
        assertThrows(BusinessException.class, () -> TextSupport.require("", "x"));
        assertThrows(BusinessException.class, () -> TextSupport.require("\u3000", "x"));
    }

    @Test
    @DisplayName("⚠ 回归钉：夹在中间的空格不清洗（只处理首尾）")
    void requireKeepsInnerWhitespace() {
        assertEquals("a  b", TextSupport.require(" a  b ", "x"));
    }

    @Test
    @DisplayName("blankToNull：null / 空串 / 全空白都归一成 null")
    void blankToNullCollapsesMissingValues() {
        assertNull(TextSupport.blankToNull(null));
        assertNull(TextSupport.blankToNull(""));
        assertNull(TextSupport.blankToNull("   "));
        assertNull(TextSupport.blankToNull("\t\n"));
        assertNull(TextSupport.blankToNull("\u3000"));
    }

    @Test
    @DisplayName("blankToNull：有内容时去首尾空白，内部原样保留")
    void blankToNullStripsOnlyEdges() {
        assertEquals("abc", TextSupport.blankToNull("  abc  "));
        assertEquals("a  b", TextSupport.blankToNull(" a  b "));
        assertEquals("abc", TextSupport.blankToNull("\u3000abc\u3000"));
    }

    @Test
    @DisplayName("⚠ 已知口径：不换行空格（U+00A0）不算空白，按内容处理")
    void nonBreakingSpaceIsContent() {
        assertEquals("\u00a0", TextSupport.blankToNull("\u00a0"));
    }
}
