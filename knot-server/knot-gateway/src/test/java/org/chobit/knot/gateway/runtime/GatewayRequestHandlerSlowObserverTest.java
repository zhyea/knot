package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.model.EjectionPolicy;
import org.chobit.knot.gateway.outage.CaffeineTargetOutageStore;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.OutageTracker;
import org.chobit.knot.gateway.outage.TargetOutageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 慢信号观察者工厂（{@link GatewayRequestHandler#slowSignalObserver}）的归属口径：
 * 命中目标从上下文属性惰性读取，取不到（异常路径无首 token）即丢弃。
 *
 * <p>属性键与 {@code GatewayRequestHandler.ATTR_HIT_TARGET} 同为 {@code "hitTarget"}
 * （私有常量，测试以同名字面量对齐）。</p>
 */
class GatewayRequestHandlerSlowObserverTest {

    private static final String ATTR_HIT_TARGET = "hitTarget";

    /** 慢阈值 1000ms、阈值 3：一次慢信号只记 1，不直接摘除。 */
    private static final EjectionPolicy POLICY = new EjectionPolicy(
            true, 60, 3, true, 1_000, 60_000,
            true, 2.0, 600_000, 30_000, true, EjectionPolicy.MODE_SIMPLE);

    private TargetOutageStore store;
    private OutageTracker tracker;
    private GatewayRequestContext context;

    @BeforeEach
    void setUp() {
        store = new CaffeineTargetOutageStore(1_000L, 60_000L);
        tracker = new OutageTracker(store, new OutageKeys("knot:outage"));
        context = new GatewayRequestContext("Bearer key", "rule-a", null, null, null);
    }

    @Test
    @DisplayName("无命中目标：慢信号静默丢弃")
    void withoutHitTargetSkips() {
        LongConsumer observer = GatewayRequestHandler.slowSignalObserver(context, tracker, POLICY, "rule-a");

        observer.accept(5_000);

        assertEquals(0, store.state(key("gpt-4o"), System.currentTimeMillis()).anomalies(),
                "异常路径没有首 token，不得凭观察者补记");
    }

    @Test
    @DisplayName("有命中目标且慢：计入 SLOW")
    void withHitTargetRecordsSlow() {
        context.putAttribute(ATTR_HIT_TARGET, target("gpt-4o"));
        LongConsumer observer = GatewayRequestHandler.slowSignalObserver(context, tracker, POLICY, "rule-a");

        observer.accept(2_000);

        assertEquals(1, store.state(key("gpt-4o"), System.currentTimeMillis()).slows());
    }

    @Test
    @DisplayName("有命中目标但快：不计")
    void withHitTargetFastSkips() {
        context.putAttribute(ATTR_HIT_TARGET, target("gpt-4o"));
        LongConsumer observer = GatewayRequestHandler.slowSignalObserver(context, tracker, POLICY, "rule-a");

        observer.accept(100);

        assertEquals(0, store.state(key("gpt-4o"), System.currentTimeMillis()).anomalies());
    }

    private static RoutingRuleTargetDto target(String modelCode) {
        return new RoutingRuleTargetDto("MODEL", 1L, modelCode, modelCode, modelCode,
                "CHAT", "provider-a", 1, true);
    }

    private String key(String modelCode) {
        return new OutageKeys("knot:outage").of("rule-a", modelCode);
    }
}
