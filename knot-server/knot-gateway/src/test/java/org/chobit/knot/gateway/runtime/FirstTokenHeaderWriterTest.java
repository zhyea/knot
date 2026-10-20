package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 首 token 响应时间写头器：口径（端到端毫秒）、幂等、无载体降级、观察者语义。
 *
 * <p>2026-10-20（WebFlux 批2）：写头载体是 {@link GatewayHttpExchange}，
 * 测试替身换成纯内存的 {@link RecordingHttpExchange}（Servlet 的
 * {@code MockHttpServletResponse} 随容器一起消失）。</p>
 */
class FirstTokenHeaderWriterTest {

    @Test
    void marksOnceWithElapsedMillis() throws InterruptedException {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        long receivedAt = System.currentTimeMillis() - 200L;
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(receivedAt, carrier, null);

        writer.mark();
        String first = carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME);
        assertNotNull(first, "mark() 必须写头");
        assertTrue(Long.parseLong(first) >= 200, "端到端口径应含 receivedAt 之前的耗时，实际 " + first + "ms");

        // 幂等：二次 mark 不得覆盖首次写入的值（SSE 每行都会触发 mark）
        Thread.sleep(30);
        writer.mark();
        assertEquals(first, carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME),
                "mark() 幂等，重复调用不得改写响应头");
    }

    @Test
    void degradesSilentlyWithoutResponse() {
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(
                System.currentTimeMillis(), GatewayHttpExchange.noop(), null);
        writer.mark();
        writer.mark();
        // 无响应载体时静默跳过，不抛异常即通过
    }

    @Test
    void noopMarkIsSilent() {
        FirstTokenHeaderWriter.noop().mark();
    }

    @Test
    void ofContextWithoutCarrierDoesNotThrow() {
        // 非 web 场景（noop 载体）时必须降级而非炸链路
        GatewayRequestContext context = new GatewayRequestContext("Bearer key", "rule-code", null, null, null);
        FirstTokenHeaderWriter writer = FirstTokenHeaderWriter.of(context, GatewayHttpExchange.noop());
        writer.mark();
        assertNotNull(writer.elapsedMillis(), "无载体也要算出耗时（数仓口径不依赖响应对象）");
    }

    @Test
    void ofContextWritesThroughCarrier() {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        GatewayRequestContext context = new GatewayRequestContext("Bearer key", "rule-code", null, null, null);

        FirstTokenHeaderWriter.of(context, carrier).mark();

        assertNotNull(carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME),
                "of(context, carrier) 必须写到载体上");
    }

    /**
     * 载体的核心契约：在请求线程上抓取后，<b>在另一个线程写头仍然生效</b>。
     *
     * <p>这正是原先靠 {@code RequestContextHolder} 隐式取 response 做不到的事
     * （异步池线程上线程本地属性为空，写头会被静默跳过）。</p>
     */
    @Test
    @DisplayName("显式载体在异步线程上仍能写头")
    void explicitCarrierWorksFromOtherThreads() throws Exception {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        GatewayRequestContext context = new GatewayRequestContext("Bearer key", "rule-code", null, null, null);

        Thread worker = new Thread(() -> FirstTokenHeaderWriter.of(context, carrier).mark());
        worker.start();
        worker.join();

        assertNotNull(carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME),
                "显式载体在异步线程上必须仍能写头");
    }

    @Test
    void observerFiresExactlyOnce() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(
                System.currentTimeMillis(), new RecordingHttpExchange(), value -> calls.incrementAndGet());

        writer.mark();
        Thread.sleep(20);
        writer.mark();
        writer.mark();
        assertEquals(1, calls.get(), "观察者必须只触发一次（与写头同步幂等）");
    }

    @Test
    void observerFiresEvenWithoutResponse() {
        AtomicLong elapsed = new AtomicLong(-1L);
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(
                System.currentTimeMillis() - 120L, GatewayHttpExchange.noop(), elapsed::set);

        writer.mark();

        assertTrue(elapsed.get() >= 120L, "无响应载体时观察者仍必须触发并收到端到端耗时，实际 " + elapsed.get() + "ms");
    }

    @Test
    void observerReceivesElapsedBeforeHeaderWrite() {
        RecordingHttpExchange carrier = new RecordingHttpExchange();
        AtomicLong observed = new AtomicLong(-1L);
        AtomicInteger headerAtObserverTime = new AtomicInteger();
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(
                System.currentTimeMillis() - 50L, carrier, value -> {
                    observed.set(value);
                    // 观察者触发时响应头尚未写入（先通知后写头）
                    headerAtObserverTime.set(carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME) == null ? 0 : 1);
                });

        writer.mark();

        assertTrue(observed.get() >= 50L, "观察者应收到耗时，实际 " + observed.get() + "ms");
        assertEquals(0, headerAtObserverTime.get(), "观察者必须先于写头触发");
        assertNotNull(carrier.responseHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME), "写头仍须完成");
    }
}
