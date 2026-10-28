package org.chobit.knot.adapter.support;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VideoRequestSupport} 的契约：递归剥掉客户端回推地址，其余一个字节都不动。
 *
 * <p>⭐ 为什么必须递归：Kling 把回推地址放在 {@code options.callback_url}，Luma / MiniMax
 * 放在顶层——只扫一层等于只挡住一半厂商，而<b>漏一家的表现是完全静默的</b>
 * （没有任何运行期信号，只有客户拿到产物、knot 那边既查不到也收不回那笔用量）。</p>
 *
 * <p>⭐ 为什么无命中必须返回同一实例：视频以外的协议也走 {@link #passthroughBody}（透传兜底），
 * 每个请求都为一次不可能命中的扫描复制整包请求体没有意义；同时
 * {@code UpstreamRequestContext.requestBody()} 是异步任务要落 {@code request_json} 复现计费的快照，
 * 原地改会把账改坏。</p>
 */
class VideoRequestSupportTest {

    /**
     * ⚠ 零拷贝的基准是 {@code context.requestBody()} 而不是调用方传进来的那个 Map：
     * 上下文的构造器已经拷了一份，两者本就不同实例。
     */
    @Test
    @DisplayName("无命中：返回同一实例（相对上下文快照零拷贝）")
    void returnsSameInstanceWhenClean() {
        UpstreamRequestContext context =
                context(body("prompt", "一只猫", "options", body("mode", "pro")));

        assertSame(context.requestBody(), VideoRequestSupport.passthroughBody(context));
    }

    @Test
    @DisplayName("顶层命中：去掉回调字段，同层其它字段逐字保留")
    void stripsTopLevelField() {
        UpstreamRequestContext context = context(body(
                "prompt", "一只猫",
                "callback_url", "https://client.example/hook",
                "duration", 10));

        Map<String, Object> sent = VideoRequestSupport.passthroughBody(context);

        assertNotSame(context.requestBody(), sent, "有命中必须给新实例");
        assertEquals(body("prompt", "一只猫", "duration", 10), sent);
        assertTrue(context.requestBody().containsKey("callback_url"),
                "快照不得被改写：异步任务要拿它落 request_json 复现计费");
    }

    @Test
    @DisplayName("嵌套 Map 命中：options.callback_url 同样剥掉")
    void stripsNestedMapField() {
        UpstreamRequestContext context = context(body("options",
                body("callback_url", "https://client.example/hook", "mode", "pro")));

        Map<String, Object> sent = VideoRequestSupport.passthroughBody(context);

        assertEquals(body("options", body("mode", "pro")), sent);
        assertTrue(nested(context.requestBody(), "options").containsKey("callback_url"),
                "嵌套层也不得被原地改写");
    }

    @Test
    @DisplayName("List 里的 Map 命中：逐个下钻")
    @SuppressWarnings("unchecked")
    void stripsInsideList() {
        Map<String, Object> hit = body("webhook_url", "https://client.example/hook", "index", 0);
        Map<String, Object> clean = body("index", 1);
        Map<String, Object> source = body("contents", List.of(hit, clean));

        Map<String, Object> body = VideoRequestSupport.passthroughBody(context(source));
        List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");

        assertEquals(body("index", 0), contents.get(0));
        assertEquals(body("index", 1), contents.get(1));
        assertTrue(hit.containsKey("webhook_url"), "列表里的元素同样不得被原地改写");
    }

    @Test
    @DisplayName("大小写与下划线/连字符归一化：callbackUrl、CALLBACK-URL 都算")
    void normalizesFieldNames() {
        Map<String, Object> source = body(
                "callbackUrl", "a",
                "CALLBACK-URL", "b",
                "Callback_Url", "c",
                "webhookUrl", "d",
                "WEBHOOK-URL", "e",
                "prompt", "一只猫");

        assertEquals(body("prompt", "一只猫"),
                VideoRequestSupport.passthroughBody(context(source)));
    }

    @Test
    @DisplayName("近似字段不误伤：callback / notify_url / my_callback_url 原样保留")
    void doesNotStripLookalikes() {
        UpstreamRequestContext context = context(body(
                "callback", "x",
                "notify_url", "y",
                "my_callback_url", "z"));

        assertSame(context.requestBody(), VideoRequestSupport.passthroughBody(context),
                "只认 CALLBACK_FIELDS 里的名字，不做前缀/子串匹配");
    }

    @Test
    @DisplayName("深层嵌套 + 混合容器：Map 里套 List 里套 Map 也要穿透")
    @SuppressWarnings("unchecked")
    void descendsThroughMixedContainers() {
        Map<String, Object> deep = body("callback_url", "https://client.example/hook", "k", 1);
        Map<String, Object> source = body("outer", body("middle", new ArrayList<>(List.of(deep))));

        Map<String, Object> body = VideoRequestSupport.passthroughBody(context(source));
        List<Map<String, Object>> middle =
                (List<Map<String, Object>>) nested(body, "outer").get("middle");

        assertEquals(body("k", 1), middle.get(0));
    }

    @Test
    @DisplayName("空上下文 / 空请求体：给空 Map 而不是 NPE")
    void handlesEmptyContext() {
        assertEquals(Map.of(), VideoRequestSupport.passthroughBody(null));
        assertEquals(Map.of(), VideoRequestSupport.passthroughBody(context(new LinkedHashMap<>())));
    }

    @Test
    @DisplayName("非字符串值不受影响：数字/布尔/null 原样带过")
    void keepsScalarValuesUntouched() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("n", 2);
        source.put("flag", false);
        source.put("none", null);
        UpstreamRequestContext context = context(source);

        Map<String, Object> body = VideoRequestSupport.passthroughBody(context);

        assertSame(context.requestBody(), body);
        assertEquals(source, body);
        assertFalse(body.containsKey("callback_url"));
    }

    private static UpstreamRequestContext context(Map<String, Object> source) {
        return new UpstreamRequestContext(
                ModelApiProtocolEnum.VIDEO_GENERATIONS, source, null, null, null, null, null,
                null, null, null, "req-1");
    }

    private static Map<String, Object> nested(Map<String, Object> body, String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) body.get(key);
        return value;
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
