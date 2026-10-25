package org.chobit.knot.plugin.runtime;

import org.chobit.knot.plugin.PluginBindingProvider;
import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginDispatchContext;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginStageCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultPluginDispatcher} 分发语义的行为测试。
 *
 * <p>盯四件事：① 绑定按 {@code orderNo} 升序执行，{@code null} 排最后；
 * ② handler 按 capability（忽略大小写）+ extensionPoint + stageCode + contextType 四要素解析；
 * ③ 任一要素不匹配即跳过该绑定（不抛异常、不中断其余绑定）；
 * ④ handler 收到的是原始 binding 与 context 对象本身（不是拷贝）。</p>
 *
 * <p>另注意：dispatch 本身没有 try/catch——handler 抛出的异常直接向上传播，
 * 失败策略（FAIL_CLOSED / FAIL_OPEN）由调用方按插件类型决定，不在这里吞。</p>
 */
class DefaultPluginDispatcherTest {

    private static final PluginExtensionPoint GATEWAY = PluginExtensionPoint.GATEWAY_EXCHANGE;
    private static final PluginStageCode REQUEST = PluginStageCode.GATEWAY_REQUEST;

    /**
     * 测试用上下文：只要一个可辨识的具体子类即可
     */
    private static final class TestContext extends PluginDispatchContext {
        private TestContext() {
            super("trace-1", "00-trace-1-01");
        }
    }

    /**
     * 另一种上下文：用于验证 contextType 不匹配时被跳过
     */
    private static final class OtherContext extends PluginDispatchContext {
        private OtherContext() {
            super("trace-2", null);
        }
    }

    /**
     * 记录被调次数与实参的 handler；descriptor / contextType 均可构造期指定
     */
    private static final class RecordingHandler implements PluginHandler<PluginDispatchContext> {

        private final PluginDescriptor descriptor;
        private final Class<? extends PluginDispatchContext> contextType;
        private final List<PluginBindingView> receivedBindings = new ArrayList<>();
        private final List<PluginDispatchContext> receivedContexts = new ArrayList<>();

        private RecordingHandler(String capabilityCode,
                                 PluginExtensionPoint extensionPoint,
                                 PluginStageCode stageCode,
                                 Class<? extends PluginDispatchContext> contextType) {
            this.descriptor = new PluginDescriptor(capabilityCode, extensionPoint, stageCode);
            this.contextType = contextType;
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<PluginDispatchContext> contextType() {
            return (Class<PluginDispatchContext>) contextType;
        }

        @Override
        public void handle(PluginBindingView binding, PluginDispatchContext context) {
            receivedBindings.add(binding);
            receivedContexts.add(context);
        }
    }

    /**
     * 固定返回绑定列表的 provider，同时记录调用方传入的扩展点与阶段
     */
    private static final class StubBindingProvider implements PluginBindingProvider {

        private final List<PluginBindingView> bindings;
        private final AtomicReference<PluginExtensionPoint> seenExtensionPoint = new AtomicReference<>();
        private final AtomicReference<PluginStageCode> seenStageCode = new AtomicReference<>();

        private StubBindingProvider(PluginBindingView... bindings) {
            this.bindings = Arrays.asList(bindings);
        }

        @Override
        public List<PluginBindingView> listBindings(PluginExtensionPoint extensionPoint, PluginStageCode stageCode) {
            seenExtensionPoint.set(extensionPoint);
            seenStageCode.set(stageCode);
            return bindings;
        }
    }

    private static PluginBindingView binding(long id, String capability, Integer orderNo) {
        return new PluginBindingView(id, "INST-" + id, "实例" + id, "PKG", "包",
            capability, GATEWAY, REQUEST, orderNo, 3000);
    }

    @Test
    void dispatchesBindingsInOrderNoAscendingWithNullLast() {
        StubBindingProvider provider = new StubBindingProvider(
            binding(1L, "CAP", 30),
            binding(2L, "CAP", 10),
            binding(3L, "CAP", null));
        RecordingHandler handler = new RecordingHandler("CAP", GATEWAY, REQUEST, TestContext.class);
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.of(handler));

        dispatcher.dispatch(GATEWAY, REQUEST, new TestContext());

        assertEquals(List.of(2L, 1L, 3L),
            handler.receivedBindings.stream().map(PluginBindingView::bindingId).toList(),
            "orderNo 升序执行，null 排最后");
        assertSame(GATEWAY, provider.seenExtensionPoint.get());
        assertSame(REQUEST, provider.seenStageCode.get());
    }

    @Test
    void resolvesHandlerOnAllFourDescriptorFactors() {
        StubBindingProvider provider = new StubBindingProvider(binding(9L, "CAP", 1));
        RecordingHandler handler = new RecordingHandler("CAP", GATEWAY, REQUEST, TestContext.class);
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.of(handler));
        TestContext context = new TestContext();

        dispatcher.dispatch(GATEWAY, REQUEST, context);

        assertEquals(1, handler.receivedBindings.size());
        assertSame(9L, handler.receivedBindings.get(0).bindingId());
        assertSame(context, handler.receivedContexts.get(0), "handler 必须收到原始 context 对象");
    }

    @Test
    void capabilityCodeMatchIgnoresCase() {
        StubBindingProvider provider = new StubBindingProvider(binding(1L, "sensitive_word", 1));
        RecordingHandler handler = new RecordingHandler("Sensitive_Word", GATEWAY, REQUEST, TestContext.class);
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.of(handler));

        dispatcher.dispatch(GATEWAY, REQUEST, new TestContext());

        assertEquals(1, handler.receivedBindings.size(), "capabilityCode 大小写不敏感");
    }

    @Test
    void skipsBindingWhenNoHandlerMatchesCapability() {
        StubBindingProvider provider = new StubBindingProvider(binding(1L, "UNKNOWN", 1));
        RecordingHandler handler = new RecordingHandler("CAP", GATEWAY, REQUEST, TestContext.class);
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.of(handler));

        dispatcher.dispatch(GATEWAY, REQUEST, new TestContext());

        assertTrue(handler.receivedBindings.isEmpty(), "无匹配 handler 的绑定被静默跳过");
    }

    @Test
    void skipsBindingWhenExtensionPointOrStageMismatch() {
        StubBindingProvider provider = new StubBindingProvider(binding(1L, "CAP", 1));
        // handler 声明的是 UPSTREAM 扩展点 / UPSTREAM_REQUEST 阶段，与绑定不符
        RecordingHandler handler = new RecordingHandler("CAP",
            PluginExtensionPoint.UPSTREAM_EXCHANGE, PluginStageCode.UPSTREAM_REQUEST, TestContext.class);
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.of(handler));

        dispatcher.dispatch(GATEWAY, REQUEST, new TestContext());

        assertTrue(handler.receivedBindings.isEmpty(), "扩展点或阶段不匹配即跳过");
    }

    @Test
    void skipsBindingWhenContextTypeMismatch() {
        StubBindingProvider provider = new StubBindingProvider(binding(1L, "CAP", 1));
        RecordingHandler handler = new RecordingHandler("CAP", GATEWAY, REQUEST, OtherContext.class);
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.of(handler));

        dispatcher.dispatch(GATEWAY, REQUEST, new TestContext());

        assertTrue(handler.receivedBindings.isEmpty(), "contextType 不匹配即跳过");
    }

    @Test
    void keepsDispatchingRemainingBindingsWhenOneHasNoHandler() {
        StubBindingProvider provider = new StubBindingProvider(
            binding(1L, "UNKNOWN", 10),
            binding(2L, "CAP", 20));
        RecordingHandler handler = new RecordingHandler("CAP", GATEWAY, REQUEST, TestContext.class);
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.of(handler));

        dispatcher.dispatch(GATEWAY, REQUEST, new TestContext());

        assertEquals(List.of(2L), handler.receivedBindings.stream().map(PluginBindingView::bindingId).toList(),
            "跳过无 handler 的绑定后，其余绑定照常执行");
    }

    @Test
    void handlerExceptionPropagatesToCaller() {
        StubBindingProvider provider = new StubBindingProvider(binding(1L, "CAP", 1));
        PluginHandler<TestContext> failing = new PluginHandler<>() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor("CAP", GATEWAY, REQUEST);
            }

            @Override
            public Class<TestContext> contextType() {
                return TestContext.class;
            }

            @Override
            public void handle(PluginBindingView binding, TestContext context) {
                throw new IllegalStateException("boom");
            }
        };
        DefaultPluginDispatcher dispatcher = new DefaultPluginDispatcher(provider, List.<PluginHandler<?>>of(failing));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> dispatcher.dispatch(GATEWAY, REQUEST, new TestContext()));
        assertEquals("boom", ex.getMessage(), "dispatch 不吞异常：失败策略由调用方决定");
    }
}
