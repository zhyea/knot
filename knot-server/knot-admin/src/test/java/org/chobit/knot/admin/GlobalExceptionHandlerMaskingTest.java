package org.chobit.knot.admin;

import org.chobit.knot.admin.rw.RwProperties;
import org.chobit.knot.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 未预期异常的<b>对外响应</b>：只给稳定文案 + 关联编号，异常原文不得外泄。
 *
 * <p>回归背景：{@code handleException} 曾把 {@code e.getMessage()} 直接拼进响应，
 * SQL、表名、类名、序列化片段都会跟着出去。管理端虽然要登录，但「异常文本」不该成为
 * 对外契约的一部分——它既不稳定（随驱动 / 版本变化），也会把内部结构带给调用方。</p>
 *
 * <p>守的是两件事：① 原文不出现；② 仍留一个可检索的关联编号，别为了脱敏把排查链路一起断掉。</p>
 */
class GlobalExceptionHandlerMaskingTest {

    @Test
    @DisplayName("内部异常原文不外泄，但保留 8 位关联编号")
    void hidesInternalMessageButKeepsErrorId() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new RwProperties());
        String leak = "Table 'ks_routing_consumers' doesn't exist; SQL: select secret_key from ks_routing_consumers";

        ApiResponse<Void> response = handler.handleException(new IllegalStateException(leak));

        assertFalse(response.success(), "未预期异常必须是失败响应");
        assertFalse(response.message().contains("ks_routing_consumers"), "表名不得随异常文本外泄");
        assertFalse(response.message().contains(leak), "异常原文不得出现在响应里");
        assertTrue(response.message().contains("错误编号"), "脱敏后必须留一个可检索的关联编号");
    }
}
