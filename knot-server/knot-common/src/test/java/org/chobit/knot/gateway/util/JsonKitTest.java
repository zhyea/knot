package org.chobit.knot.gateway.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定「解析失败的日志摘要不得泄漏原文」这一契约。
 *
 * <p>背景：2026-09-23 供应商凭据解密后不是 JSON 对象，{@code fromJson} 把原文
 * （明文 apiKey）直接写进了 ERROR 日志。本测试保证摘要只含长度与首字符。
 */
class JsonKitTest {

    private static final int LEAK_WINDOW = 8;

    @Test
    void credentialPayloadIsRedacted() {
        String payload = "{\"apiKey\":\"sk-deepseek-leak-canary-12345\"}";
        String digest = JsonKit.digestForLog(payload);
        assertEquals("len=" + payload.length() + ", first={", digest);
        assertNoLeak(digest, payload);
        assertFalse(digest.contains("apiKey"), "键名也不应出现在日志里: " + digest);
    }

    @Test
    void bareSecretTokenIsRedacted() {
        String payload = "sk-deepseek-demo-xxxxx";
        String digest = JsonKit.digestForLog(payload);
        assertEquals("len=" + payload.length() + ", first=s", digest);
        assertNoLeak(digest, payload);
    }

    @Test
    void leadingWhitespaceIsTrimmedBeforePickingFirstChar() {
        String payload = "  \n\t[1,2]  ";
        assertEquals("len=" + payload.length() + ", first=[", JsonKit.digestForLog(payload));
    }

    @Test
    void nonPrintableFirstCharIsEscaped() {
        assertEquals("len=4, first=\\u007f", JsonKit.digestForLog("\u007Fabc"));
    }

    @Test
    void nullAndBlankPayloads() {
        assertEquals("null", JsonKit.digestForLog(null));
        assertEquals("blank(len=0)", JsonKit.digestForLog(""));
        assertEquals("blank(len=3)", JsonKit.digestForLog("   "));
    }

    @Test
    void fromJsonFailureStillReturnsNullAndDoesNotThrow() {
        assertNull(JsonKit.fromJson("sk-deepseek-demo-xxxxx", new TypeReference<Map<String, Object>>() {
        }));
        assertNull(JsonKit.fromJson("sk-deepseek-demo-xxxxx", Map.class));
        assertNull(JsonKit.parse("sk-deepseek-demo-xxxxx"));
    }

    private static void assertNoLeak(String digest, String secret) {
        assertFalse(digest.contains(secret), "摘要包含完整原文: " + digest);
        for (int i = 0; i + LEAK_WINDOW <= secret.length(); i++) {
            String window = secret.substring(i, i + LEAK_WINDOW);
            assertFalse(digest.contains(window), "摘要泄漏了原文片段 [" + window + "]: " + digest);
        }
        assertTrue(digest.startsWith("len="), "摘要应保留长度以便定位: " + digest);
    }
}
