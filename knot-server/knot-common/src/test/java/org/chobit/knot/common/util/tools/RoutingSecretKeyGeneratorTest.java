package org.chobit.knot.common.util.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ⭐ secret key 的入站校验口径：<b>只卡前缀与长度上限</b>，不校验字符集、不校验定长。
 *
 * <p>为什么不是严格格式：生成器确实产出 {@code sk-} + 32 hex，但库内存量 key 是手工造的
 * （2026-11-11 普查：开发库 4 行长度 25/29/30/32，全部不满足 {@code ^sk-[0-9a-f]{32}$}）。
 * 直接上严格校验会一次性打断这些调用方，而生产库本机没有、存量未知。所以先只挡
 * 「异常超长输入」——那个维度的攻击面是「每次换一把超长 key 就能在缓存与日志里制造无界条目」。</p>
 *
 * <p>等生产库普查完、存量 key 归一后，再在这里把断言换成严格格式：届时本文件是唯一要改的地方。</p>
 */
class RoutingSecretKeyGeneratorTest {

    /**
     * 上限刻意在测试里写死一遍（不从实现里取值）：改上限等于改入站口径，
     * 必须同时改实现与这条断言，让「悄悄放宽」在评审里可见。
     */
    private static final int MAX_LENGTH = 128;

    @Test
    @DisplayName("生成器产物自洽：sk- + 32 hex，共 35 字符，且能通过校验")
    void generatedKeyIsAccepted() {
        String key = RoutingSecretKeyGenerator.generate();
        assertEquals(35, key.length());
        assertTrue(key.startsWith("sk-"));
        assertTrue(key.matches("sk-[0-9a-f]{32}"), "生成器口径必须是 sk- + 32 位小写 hex");
        assertTrue(RoutingSecretKeyGenerator.isRoutingSecretKey(key));
    }

    @Test
    @DisplayName("⭐ 存量手工 key（长度 25/29/30/32）仍必须通过：收紧不能打断既有调用方")
    void handMadeLegacyKeysAreAccepted() {
        for (int len : new int[]{25, 29, 30, 32}) {
            String legacy = "sk-" + "a".repeat(len - 3);
            assertEquals(len, legacy.length());
            assertTrue(RoutingSecretKeyGenerator.isRoutingSecretKey(legacy),
                    "长度 " + len + " 的手工 key 必须仍被接受");
        }
    }

    @Test
    @DisplayName("空值 / 缺前缀 / 前缀后为空一律拒绝")
    void emptyAndMalformedRejected() {
        assertFalse(RoutingSecretKeyGenerator.isRoutingSecretKey(null));
        assertFalse(RoutingSecretKeyGenerator.isRoutingSecretKey(""));
        assertFalse(RoutingSecretKeyGenerator.isRoutingSecretKey("sk-"));
        assertFalse(RoutingSecretKeyGenerator.isRoutingSecretKey("ak-0123456789abcdef"));
        assertFalse(RoutingSecretKeyGenerator.isRoutingSecretKey("SK-0123456789abcdef"));
    }

    @Test
    @DisplayName("⭐ 长度上限边界：128 字符放行，129 字符拒绝")
    void overlongKeyIsRejected() {
        String atLimit = "sk-" + "a".repeat(MAX_LENGTH - 3);
        assertEquals(MAX_LENGTH, atLimit.length());
        assertTrue(RoutingSecretKeyGenerator.isRoutingSecretKey(atLimit), "恰好 128 字符必须放行");

        String oneOver = "sk-" + "a".repeat(MAX_LENGTH - 2);
        assertEquals(MAX_LENGTH + 1, oneOver.length());
        assertFalse(RoutingSecretKeyGenerator.isRoutingSecretKey(oneOver),
                "超过上限必须拒绝，否则超长 key 能持续制造缓存条目");

        String wayOver = "sk-" + "a".repeat(4096);
        assertFalse(RoutingSecretKeyGenerator.isRoutingSecretKey(wayOver));
    }
}
