package org.chobit.knot.adapter.usage.extractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VideoUsageFacts} 的契约：<b>一次解析取齐十个档位事实</b>，厂商用
 * {@code toBuilder()} 只改自己那几项。
 *
 * <p>这里守两件事，都是「十个钩子」时代的老毛病：</p>
 * <ol>
 *   <li><b>解析不全</b>：某维度忘了从 {@code of()} 里取 → 一律 0 / false / null，
 *       计费侧少一路维度键，静默少收或错档。所以第一例把 10 项全部断言一遍。</li>
 *   <li><b>toBuilder 覆盖过头</b>：子类改一项时把其余项连带重置（例如重新 {@code new} 一份记录），
 *       表现是「厂商只改了有声，分辨率却丢了」——档位回落默认价，同样静默。</li>
 * </ol>
 */
class VideoUsageFactsTest {

    @Test
    @DisplayName("一次解析取齐十个事实：响应体 2 项 + 请求体 8 项")
    void parsesEveryFactInOnePass() {
        Map<String, Object> body = body(
                "usage", body("duration_seconds", 5.5),
                "data", List.of(body("url", "https://x/v.mp4"), body("url", "https://x/v2.mp4")));

        Map<String, Object> requestBody = body(
                "size", "1080p",
                "aspect_ratio", "16:9",
                "mode", "MOTION_CONTROL",
                "input_video_seconds", 3,
                "images", List.of("https://x/a.png", "https://x/b.png"),
                "generate_audio", true,
                "voice_id", "male-qn-qingse",
                "video_url", "https://x/in.mp4");

        VideoUsageFacts facts = VideoUsageFacts.of(body, requestBody);

        assertEquals(5_500L, facts.outputVideoMillis(), "小数秒保留精度");
        assertEquals("1080P", facts.outputResolution());
        assertEquals("16:9", facts.outputAspectRatio());
        assertEquals("MOTION_CONTROL", facts.outputMode());
        assertEquals(2L, facts.outputVideoCount());
        assertEquals(3_000L, facts.inputVideoMillis());
        assertEquals(2L, facts.inputImageCount());
        assertTrue(facts.sound());
        assertTrue(facts.voiceSpecified());
        assertTrue(facts.referenceVideo());
    }

    @Test
    @DisplayName("什么都没声明时不猜：字符串 null、数值 0、布尔 false")
    void leavesUndeclaredFactsEmpty() {
        VideoUsageFacts facts = VideoUsageFacts.of(body(), body());

        assertNull(facts.outputResolution(), "未声明分辨率 → null（模型侧归一成空串，回落默认档）");
        assertNull(facts.outputAspectRatio());
        assertNull(facts.outputMode());
        assertEquals(0L, facts.outputVideoMillis());
        assertEquals(0L, facts.outputVideoCount());
        assertEquals(0L, facts.inputVideoMillis());
        assertEquals(0L, facts.inputImageCount());
        assertFalse(facts.sound());
        assertFalse(facts.voiceSpecified());
        assertFalse(facts.referenceVideo());
    }

    @Test
    @DisplayName("usage 里的时长优先于响应体顶层：一次解析没有把 usage 段丢掉")
    void usageWinsOverTopLevelDuration() {
        Map<String, Object> body = body("duration_seconds", 9, "usage", body("duration_seconds", 4));

        assertEquals(4_000L, VideoUsageFacts.of(body, body()).outputVideoMillis());
    }

    @Test
    @DisplayName("存在性 vs 布尔开关的分工没被合并：video_url 字符串算「有」，generate_audio=\"false\" 算「无」")
    void keepsPresentAndFlagSemanticsApart() {
        VideoUsageFacts withUrl = VideoUsageFacts.of(body(),
                body("video_url", "https://x/in.mp4", "generate_audio", "false"));

        assertTrue(withUrl.referenceVideo(), "URL 字符串必须走存在性语义");
        assertFalse(withUrl.sound(), "显式 false 的开关就是关");
    }

    @Test
    @DisplayName("toBuilder 只改指定项：其余保持通用解析结果")
    void toBuilderChangesOnlyTargetedFacts() {
        Map<String, Object> requestBody = body("size", "720p", "images", List.of("https://x/a.png"));
        VideoUsageFacts generic = VideoUsageFacts.of(body("duration_seconds", 6), requestBody);

        VideoUsageFacts vendor = generic.toBuilder()
                .sound(true)
                .referenceVideo(false)
                .build();

        assertNotSame(generic, vendor, "toBuilder 必须给新实例");
        assertEquals("720P", vendor.outputResolution(), "未被改的档位必须原样保留（档位名由解析层归一）");
        assertEquals(1L, vendor.inputImageCount());
        assertEquals(6_000L, vendor.outputVideoMillis());
        assertTrue(vendor.sound());
        assertFalse(generic.sound(), "原实例不受影响");
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
