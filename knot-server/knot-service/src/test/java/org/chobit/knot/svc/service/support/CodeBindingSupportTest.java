package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 改码守卫的单测：口径是「编码未变一律放行；编码变了且有引用一律 409」。
 */
class CodeBindingSupportTest {

    @Test
    void unchangedCodePassesEvenWhenBound() {
        assertDoesNotThrow(() -> CodeBindingSupport.requireCodeChangeAllowed("code-a", "code-a", 5L, "供应商编码"));
    }

    @Test
    void unchangedCodePassesDespiteSurroundingWhitespace() {
        assertDoesNotThrow(() -> CodeBindingSupport.requireCodeChangeAllowed("code-a", "  code-a ", 5L, "供应商编码"));
    }

    @Test
    void changedCodePassesWhenNotBound() {
        assertDoesNotThrow(() -> CodeBindingSupport.requireCodeChangeAllowed("code-a", "code-b", 0L, "供应商编码"));
        assertDoesNotThrow(() -> CodeBindingSupport.requireCodeChangeAllowed("code-a", "code-b", null, "供应商编码"));
    }

    @Test
    void changedCodeRejectedWhenBound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> CodeBindingSupport.requireCodeChangeAllowed("code-a", "code-b", 3L, "供应商编码"));
        assertEquals(ErrorCode.CONFLICT.code(), ex.getCode());
        assertTrue(ex.getMessage().contains("供应商编码"));
        assertTrue(ex.getMessage().contains("3"));
    }

    @Test
    void nullOldCodeIsTreatedAsEmpty() {
        // 库中旧值为 null 时按空串比较：新值非空即视为改码
        assertThrows(BusinessException.class,
                () -> CodeBindingSupport.requireCodeChangeAllowed(null, "code-b", 1L, "消费者编码"));
        assertDoesNotThrow(() -> CodeBindingSupport.requireCodeChangeAllowed(null, null, 1L, "消费者编码"));
    }

    @Test
    void countHelpersTreatNullAsZero() {
        assertEquals(0L, CodeBindingSupport.countOf(null));
        assertEquals(7L, CodeBindingSupport.countOf(7L));
        assertFalse(CodeBindingSupport.isBound(null));
        assertFalse(CodeBindingSupport.isBound(0L));
        assertTrue(CodeBindingSupport.isBound(1L));
    }

    @Test
    void normalizeTrimsAndFallsBackToEmpty() {
        assertEquals("", CodeBindingSupport.normalize(null));
        assertEquals("", CodeBindingSupport.normalize("   "));
        assertEquals("abc", CodeBindingSupport.normalize("  abc  "));
        assertTrue(CodeBindingSupport.isSameCode("Abc", " Abc "));
        assertFalse(CodeBindingSupport.isSameCode("Abc", "abc"));
    }
}
