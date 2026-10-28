package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：可灵视频请求体转换（{@link KlingVideoUpstreamAdapter} → {@code KlingVideoSupport}）。
 *
 * <p>这里守的是两条**资损防线**：非标分辨率必须当场拒绝（否则静默回落默认价），
 * {@code n>1} 必须当场拒绝（否则收一次钱交一份货）。两者在入口都是一行校验，
 * 但漏掉之后要到月末对账才会被发现。</p>
 */
class KlingVideoUpstreamAdapterTest {

    private static final String REQUEST_ID = "a1b2c3d4e5f60718293a4b5c6d7e8f90";

    private final KlingVideoUpstreamAdapter adapter = new KlingVideoUpstreamAdapter();

    @Test
    @DisplayName("身份：VIDEO_GENERATIONS 单协议 + 不出流式")
    void identity() {
        assertEquals("KLING_VIDEO", adapter.code());
        assertEquals(1, adapter.protocol().size());
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.VIDEO_GENERATIONS));
        assertFalse(adapter.streamSupported(null), "Kling create 只回 task_id，没有 SSE 可转");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("文生视频：prompt 顶层、size/duration 进 settings")
    void textToVideo() {
        Map<String, Object> body = build(Map.of("prompt", "一只猫", "size", "1080p", "duration", 10));
        assertEquals("一只猫", body.get("prompt"));
        assertNull(body.get("contents"), "文生视频不拼 contents");
        assertEquals(Map.of("resolution", "1080p", "duration", 10), body.get("settings"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("图生视频：prompt 与首帧进 contents，顶层不再放 prompt")
    void imageToVideo() {
        Map<String, Object> body = build(Map.of("prompt", "让它动起来", "image", "https://x/first.png"));
        assertNull(body.get("prompt"));
        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
        assertEquals(2, contents.size());
        assertEquals(Map.of("type", "prompt", "text", "让它动起来"), contents.get(0));
        assertEquals(Map.of("type", "first_frame", "url", "https://x/first.png"), contents.get(1));
    }

    @Test
    @DisplayName("image 写成单元素数组也认（兼容客户端把单个值写成数组）")
    void imageAsArray() {
        Map<String, Object> body = build(Map.of("image", List.of("https://x/first.png")));
        assertTrue(body.containsKey("contents"));
    }

    @Test
    @DisplayName("未声明 size / duration 时不发 settings：不代厂商发明默认值")
    void omitsSettingsWhenNotDeclared() {
        Map<String, Object> body = build(Map.of("prompt", "一只猫"));
        assertNull(body.get("settings"));
    }

    @Test
    @DisplayName("size 大小写不敏感，下发 Kling 的小写形态")
    void sizeIsCaseInsensitive() {
        Map<String, Object> body = build(Map.of("prompt", "一只猫", "size", "720P"));
        assertEquals("720p", nested(body, "settings", "resolution"));
    }

    @Test
    @DisplayName("非标分辨率当场拒绝：不得静默回落默认价")
    void unsupportedResolutionIsRejected() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> build(Map.of("prompt", "一只猫", "size", "2k")));
        assertTrue(error.getMessage().contains("2k"), error.getMessage());
        // 探针：4K 也不在本模型价表里，同样必须拒绝——否则会被 normalizeResolution 折成 "4K"
        assertThrows(BusinessException.class, () -> build(Map.of("prompt", "一只猫", "size", "1920x1080x2")));
    }

    @Test
    @DisplayName("n>1 当场拒绝：静默忽略等于收一次钱交一份货")
    void batchIsRejected() {
        assertThrows(BusinessException.class, () -> build(Map.of("prompt", "一只猫", "n", 2)));
        // 探针：n=1 必须放行，否则会把「合法的单条请求」一起拦掉
        assertEquals("一只猫", build(Map.of("prompt", "一只猫", "n", 1)).get("prompt"));
    }

    @Test
    @DisplayName("既无 prompt 又无 image：在入口拦，而不是把一次无效出站调用发出去")
    void promptOrImageIsRequired() {
        assertThrows(BusinessException.class, () -> build(Map.of("size", "720p")));
    }

    @Test
    @DisplayName("external_task_id 取网关 requestId：与异步层回填同一个值（反查上游用）")
    void externalTaskIdIsRequestId() {
        Map<String, Object> body = build(Map.of("prompt", "一只猫"));
        assertEquals(Map.of("external_task_id", REQUEST_ID), body.get("options"));
    }

    @Test
    @DisplayName("非整数 duration 拒绝：别把字符串算术的错误留到上游")
    void nonNumericDurationIsRejected() {
        assertThrows(BusinessException.class,
                () -> build(Map.of("prompt", "一只猫", "duration", "ten")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> build(Map<String, Object> source) {
        UpstreamRequestContext context = new UpstreamRequestContext(
                ModelApiProtocolEnum.VIDEO_GENERATIONS, source, null, null, null, null, null,
                null, null, null, REQUEST_ID);
        return (Map<String, Object>) adapter.buildRequestBody(context);
    }

    private static Object nested(Map<String, Object> body, String outer, String inner) {
        Object node = body.get(outer);
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> cast = new LinkedHashMap<>();
        map.forEach((k, v) -> cast.put(String.valueOf(k), v));
        return cast.get(inner);
    }
}
