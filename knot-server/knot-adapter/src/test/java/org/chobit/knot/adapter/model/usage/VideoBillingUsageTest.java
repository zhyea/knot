package org.chobit.knot.adapter.model.usage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VideoBillingUsage} 的构造与合并口径。
 *
 * <p>⭐ {@code @Builder} 不是「绕过构造器」：Lombok 的 {@code build()} 调用的仍是规范构造器，
 * 紧凑构造器里的归一（null token、字符串 trim、负数归零）必须照常生效。若哪天有人把归一
 * 搬进 builder 或另开一条 setter 路径，本类第一条用例会变红。</p>
 */
class VideoBillingUsageTest {

    @Test
    void shouldStillNormalizeThroughCompactConstructorWhenBuilt() {
        VideoBillingUsage usage = VideoBillingUsage.builder()
                .tokens(null)
                .outputVideoMillis(-1L)
                .inputVideoMillis(-1L)
                .inputImageCount(-1L)
                .outputResolution(" 720P ")
                .build();

        assertEquals(TokenBillingUsage.empty(), usage.tokens(), "null token 归一为空用量");
        assertEquals(0L, usage.outputVideoMillis(), "负数归零");
        assertEquals(0L, usage.inputVideoMillis(), "负数归零");
        assertEquals(0L, usage.inputImageCount(), "负数归零");
        assertEquals("720P", usage.outputResolution(), "字符串 trim");
    }

    @Test
    void shouldBuildSameEmptyUsageAsEmptyFactory() {
        assertEquals(VideoBillingUsage.empty(), VideoBillingUsage.builder().build());
    }

    @Test
    void shouldNotCountAttributesAsQuantityForEmptyCheck() {
        VideoBillingUsage attributesOnly = VideoBillingUsage.builder()
                .outputAspectRatio("16:9")
                .outputMode("MOTION_CONTROL")
                .sound(true)
                .voiceSpecified(true)
                .referenceVideo(true)
                .build();

        assertTrue(attributesOnly.isEmpty(),
                "属性不是量：只有声/音色/参考/画幅/模式不足以出账，否则提交态回执会被判成有量");
    }

    @Test
    void shouldCountVideoCountAsQuantityForEmptyCheck() {
        // 按条计价（Veo）：秒数、分辨率都可能缺，条数本身就是要出的账
        assertFalse(VideoBillingUsage.builder().outputVideoCount(2L).build().isEmpty());
    }

    @Test
    void shouldFallbackToOneBilledVideoWhenCountMissing() {
        assertEquals(1L, VideoBillingUsage.empty().billedVideoCount(), "0 条兜成 1 条，否则整单 0 元");
        assertEquals(2L, VideoBillingUsage.builder().outputVideoCount(2L).build().billedVideoCount(),
                "有上报值时原样使用");
    }

    @Test
    void shouldMergeByMaxAndFirstNonBlank() {
        VideoBillingUsage first = VideoBillingUsage.builder()
                .outputVideoMillis(5_000L)
                .outputVideoCount(1L)
                .inputVideoMillis(0L)
                .sound(false)
                .voiceSpecified(false)
                .referenceVideo(false)
                .build();
        VideoBillingUsage second = VideoBillingUsage.builder()
                .outputVideoMillis(3_000L)
                .outputResolution("720P")
                .outputAspectRatio("16:9")
                .outputMode("MOTION_CONTROL")
                .outputVideoCount(2L)
                .inputVideoMillis(2_000L)
                .sound(true)
                .voiceSpecified(true)
                .referenceVideo(true)
                .build();

        VideoBillingUsage merged = first.mergeMax(second);

        assertEquals(5_000L, merged.outputVideoMillis(), "数值取 max");
        assertEquals(2L, merged.outputVideoCount(), "数值取 max");
        assertEquals(2_000L, merged.inputVideoMillis(), "数值取 max");
        assertEquals("720P", merged.outputResolution(), "字符串取先出现的非空值");
        assertEquals("16:9", merged.outputAspectRatio(), "字符串取先出现的非空值");
        assertEquals("MOTION_CONTROL", merged.outputMode(), "字符串取先出现的非空值");
        assertTrue(merged.sound(), "布尔取或");
        assertTrue(merged.voiceSpecified(), "布尔取或");
        assertTrue(merged.referenceVideo(), "布尔取或");
        assertSame(second, second.mergeMax(TokenBillingUsage.empty()),
                "类型不匹配时原样返回自己");
    }
}
