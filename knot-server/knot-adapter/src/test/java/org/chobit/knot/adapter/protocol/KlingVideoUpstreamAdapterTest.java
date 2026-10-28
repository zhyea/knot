package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：可灵视频适配器 {@link KlingVideoUpstreamAdapter} 是<b>纯透传 + 剥回推地址</b>。
 *
 * <p>这里守的不是「转换对不对」（没有转换），而是<b>没有转换</b>：任何把字段搬来搬去、
 * 补默认值、按白名单校验的行为都会让下列断言变红。理由是网关是代理不是转译层——
 * 请求形状由客户端按上游文档自己写，写错了让上游 400 原路返回；
 * 网关一旦插手形状，① 每家厂商的字段语义要在这里重实现一遍、必然与文档漂移，
 * ② 顺手做的「兜底默认值」会把错参静默吞掉（判例：白名单外的分辨率被折成裸串后
 * 静默回落默认价，一次错价要等月末对账才发现）。</p>
 *
 * <p>另一条防线是<b>回推地址必须剥掉</b>：漏给供应商，供应商直接把终态推给客户端，
 * 那笔用量绕过 knot 的轮询与终态结算，既查不到也收不回。详见 {@code VideoRequestSupport}。</p>
 */
class KlingVideoUpstreamAdapterTest {

    private static final String REQUEST_ID = "a1b2c3d4e5f60718293a4b5c6d7e8f90";

    private final KlingVideoUpstreamAdapter adapter = new KlingVideoUpstreamAdapter();

    @Test
    @DisplayName("身份：VIDEO_GENERATIONS 单协议 + code 即绑定值")
    void identity() {
        assertEquals("KLING_VIDEO", adapter.code());
        assertEquals(1, adapter.protocol().size());
        assertTrue(adapter.protocol().contains(ModelApiProtocolEnum.VIDEO_GENERATIONS));
    }

    /**
     * 流式与否由<b>协议</b>决定：{@code VIDEO_GENERATIONS} 在枚举里已是 {@code streamSupported=false}，
     * 视频又走 {@code MediaProtocolExecutor}（只放行 AUDIO_SPEECH），两道都不在适配器上。
     *
     * <p>所以这里断言的是「适配器<b>没有</b>再声明一次」——用 {@code getDeclaringClass} 判：
     * 覆写回来会让后来人误以为「是这个适配器关掉了流式」，改协议时找不到真正的地儿。</p>
     */
    @Test
    @DisplayName("流式不由适配器声明：协议枚举已关，适配器不重复覆写")
    void streamFlagIsNotRedeclaredHere() throws Exception {
        assertFalse(ModelApiProtocolEnum.VIDEO_GENERATIONS.streamSupported(),
                "VIDEO_GENERATIONS 应已是 streamSupported=false");
        assertEquals(UpstreamAdapter.class,
                KlingVideoUpstreamAdapter.class
                        .getMethod("streamSupported", UpstreamRequestContext.class)
                        .getDeclaringClass(),
                "适配器不应覆写 streamSupported：那是协议层的事实");
    }

    @Test
    @DisplayName("形状原样透传：不拼 contents、不造 settings、不改大小写")
    void passesBodyThroughVerbatim() {
        Map<String, Object> source = body("prompt", "一只猫", "size", "1080P", "duration", 10);
        Map<String, Object> sent = build(source);

        assertEquals(source, sent, "请求体必须逐字一致");
        assertNull(sent.get("contents"), "不替客户端把 prompt 搬进 contents");
        assertNull(sent.get("settings"), "不代厂商发明 settings 包装");
        assertEquals("1080P", sent.get("size"), "不归一化客户端给的取值");
    }

    @Test
    @DisplayName("错参原样放行：网关不校验，让上游 400 原路返回")
    void invalidPayloadIsForwardedNotRejected() {
        Map<String, Object> source = body(
                "prompt", "一只猫", "size", "2k", "duration", "ten", "n", 2);
        Map<String, Object> sent = build(source);

        assertEquals(source, sent, "白名单外的分辨率 / 非数字时长 / n>1 都不该在这里被拦");
    }

    @Test
    @DisplayName("不注入 external_task_id：上游任务号只用创建响应里的")
    void doesNotInjectExternalTaskId() {
        Map<String, Object> sent = build(body("prompt", "一只猫"));
        assertNull(sent.get("options"), "options 是客户端的东西，网关不往里塞字段");
        assertFalse(sent.containsValue(REQUEST_ID), "requestId 不该出现在出站请求体里");
    }

    @Test
    @DisplayName("顶层 callback_url / webhook_url 剥掉")
    void stripsTopLevelCallbackFields() {
        Map<String, Object> source = body(
                "prompt", "一只猫",
                "callback_url", "https://client.example/hook",
                "webhook_url", "https://client.example/hook2");

        Map<String, Object> sent = build(source);

        assertEquals(Map.of("prompt", "一只猫"), sent);
        assertFalse(sent.containsKey("callback_url"));
        assertFalse(sent.containsKey("webhook_url"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("嵌套回推地址同样剥：Kling 放 options.callback_url，Luma 放顶层")
    void stripsNestedCallbackField() {
        Map<String, Object> options = body(
                "mode", "pro",
                "callback_url", "https://client.example/hook");
        Map<String, Object> source = body("prompt", "一只猫", "options", options);

        Map<String, Object> sent = build(source);
        Map<String, Object> sentOptions = (Map<String, Object>) sent.get("options");

        assertEquals(Map.of("mode", "pro"), sentOptions, "只剥回推地址，同层其它字段照旧");
        assertEquals("一只猫", sent.get("prompt"));
    }

    @Test
    @DisplayName("请求体快照不被改写：异步任务还要拿它落 request_json 复现计费")
    void keepsRequestBodySnapshotIntact() {
        Map<String, Object> source = body(
                "prompt", "一只猫", "callback_url", "https://client.example/hook");
        UpstreamRequestContext context = context(source);

        Map<String, Object> sent = build(context);

        assertNotSame(context.requestBody(), sent, "有命中必须给新实例，不能把快照改坏");
        assertTrue(context.requestBody().containsKey("callback_url"), "快照里原字段必须还在");
        assertEquals(source, context.requestBody(), "快照内容逐字不变");
    }

    @Test
    @DisplayName("无可剥字段时零拷贝：返回同一个 Map 实例")
    void returnsSameInstanceWhenNothingToStrip() {
        UpstreamRequestContext context = context(body("prompt", "一只猫"));
        assertSame(context.requestBody(), adapter.buildRequestBody(context));
    }

    @Test
    @DisplayName("空上下文返回空体而不是 NPE")
    void nullContextYieldsEmptyBody() {
        assertEquals(Map.of(), adapter.buildRequestBody(null));

        UpstreamRequestContext empty = context(new LinkedHashMap<>());
        assertEquals(Map.of(), adapter.buildRequestBody(empty));
    }

    private Map<String, Object> build(Map<String, Object> source) {
        return build(context(source));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> build(UpstreamRequestContext context) {
        return (Map<String, Object>) adapter.buildRequestBody(context);
    }

    private static UpstreamRequestContext context(Map<String, Object> source) {
        return new UpstreamRequestContext(
                ModelApiProtocolEnum.VIDEO_GENERATIONS, source, null, null, null, null, null,
                null, null, null, REQUEST_ID);
    }

    /** 交错的 key/value，保持插入顺序（断言按内容相等，顺序不影响，但读起来贴近真实请求） */
    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
