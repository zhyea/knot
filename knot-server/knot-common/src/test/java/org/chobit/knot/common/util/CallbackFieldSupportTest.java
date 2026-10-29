package org.chobit.knot.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CallbackFieldSupport} 的契约：字段名归一化 + 递归提取。
 *
 * <p>⭐ 这套判据被两条互不依赖的链路共用（适配器剥除、网关提取），所以在这里钉死，
 * 而不是各自在消费方的测试里顺手验一下——漏掉任何一家叫法，表现都是「那家客户静默失效」。</p>
 */
class CallbackFieldSupportTest {

    @Test
    @DisplayName("字段名归一化：忽略大小写与 _ / -")
    void fieldNameNormalization() {
        assertTrue(CallbackFieldSupport.isCallbackField("callback_url"));
        assertTrue(CallbackFieldSupport.isCallbackField("callbackUrl"));
        assertTrue(CallbackFieldSupport.isCallbackField("CALLBACK-URL"));
        assertTrue(CallbackFieldSupport.isCallbackField("webhook_url"));
        assertFalse(CallbackFieldSupport.isCallbackField("url"));
        assertFalse(CallbackFieldSupport.isCallbackField(null));
    }

    @Test
    @DisplayName("顶层 callback_url / webhook_url 直接取到")
    void topLevelFields() {
        assertEquals("https://a.example/cb",
                CallbackFieldSupport.callbackUrlOf(Map.of("callback_url", "https://a.example/cb")));
        assertEquals("https://a.example/cb",
                CallbackFieldSupport.callbackUrlOf(Map.of("webhook_url", "https://a.example/cb")));
        assertEquals("https://a.example/cb",
                CallbackFieldSupport.callbackUrlOf(Map.of("callbackUrl", "https://a.example/cb")));
    }

    @Test
    @DisplayName("⭐ 嵌套也要取到：Kling 把地址放在 options.callback_url")
    void nestedField() {
        Map<String, Object> body = Map.of(
                "prompt", "a cat",
                "options", Map.of("callback_url", "https://kling.example/cb"));
        assertEquals("https://kling.example/cb", CallbackFieldSupport.callbackUrlOf(body));
    }

    @Test
    @DisplayName("数组里的嵌套同样下钻")
    void nestedInList() {
        Map<String, Object> body = Map.of(
                "items", List.of(Map.of("webhookUrl", "https://list.example/cb")));
        assertEquals("https://list.example/cb", CallbackFieldSupport.callbackUrlOf(body));
    }

    @Test
    @DisplayName("本层直挂优先于更深层：顶层与嵌套同时出现时取顶层")
    void topLevelWins() {
        Map<String, Object> body = Map.of(
                "callback_url", "https://top.example/cb",
                "options", Map.of("callback_url", "https://nested.example/cb"));
        assertEquals("https://top.example/cb", CallbackFieldSupport.callbackUrlOf(body));
    }

    @Test
    @DisplayName("空值 / 非字符串 / 没有字段：视为未配置")
    void absentOrNonString() {
        assertNull(CallbackFieldSupport.callbackUrlOf(null));
        assertNull(CallbackFieldSupport.callbackUrlOf(Map.of()));
        assertNull(CallbackFieldSupport.callbackUrlOf(Map.of("callback_url", "  ")));
        assertNull(CallbackFieldSupport.callbackUrlOf(Map.of("callback_url", 123)));
    }
}
