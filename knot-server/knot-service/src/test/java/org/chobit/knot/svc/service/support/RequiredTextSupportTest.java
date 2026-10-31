package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 契约测试：必填文本校验的归一与拒绝口径。
 *
 * <p>⚠ 收口前 {@code ModelServiceTest} 里<b>没有任何空值用例</b>，所以「空白请求体到底报不报错」
 * 一直只由调用方代码形态保证、没有被钉住。</p>
 */
class RequiredTextSupportTest {

    @Test
    @DisplayName("正常文本：去首尾空白后原样返回")
    void trimsSurroundingWhitespace() {
        assertEquals("abc", RequiredTextSupport.require("  abc  ", "model name is required"));
        assertEquals("a b", RequiredTextSupport.require("\ta b\n", "x"));
    }

    @Test
    @DisplayName("null：抛 VALIDATION_ERROR，且文案用调用方给的那句")
    void rejectsNullWithCallerMessage() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> RequiredTextSupport.require(null, "model name is required"));
        assertEquals(ErrorCode.VALIDATION_ERROR.code(), ex.getCode());
        assertEquals("model name is required", ex.getMessage());
    }

    @Test
    @DisplayName("全空白：空格 / 制表符 / 换行一律拒绝")
    void rejectsBlankText() {
        assertThrows(BusinessException.class, () -> RequiredTextSupport.require("   ", "x"));
        assertThrows(BusinessException.class, () -> RequiredTextSupport.require("\t\n", "x"));
        assertThrows(BusinessException.class, () -> RequiredTextSupport.require("", "x"));
    }

    @Test
    @DisplayName("⚠ 回归钉：夹在中间的空格不清洗（本类只 trim 首尾，不做内部归一）")
    void keepsInnerWhitespace() {
        assertEquals("a  b", RequiredTextSupport.require(" a  b ", "x"));
    }
}
