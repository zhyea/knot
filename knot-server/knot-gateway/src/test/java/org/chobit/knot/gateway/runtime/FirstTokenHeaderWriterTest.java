package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.constants.GatewayHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 首 token 响应时间写头器：口径（端到端毫秒）、幂等、无 response 降级、观察者语义。
 */
class FirstTokenHeaderWriterTest {

    @BeforeEach
    @AfterEach
    void clearRequestAttributes() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void marksOnceWithElapsedMillis() throws InterruptedException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        long receivedAt = System.currentTimeMillis() - 200L;
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(receivedAt, response, null);

        writer.mark();
        String first = response.getHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME);
        assertNotNull(first, "mark() 必须写头");
        assertTrue(Long.parseLong(first) >= 200, "端到端口径应含 receivedAt 之前的耗时，实际 " + first + "ms");

        // 幂等：二次 mark 不得覆盖首次写入的值（SSE 每行都会触发 mark）
        Thread.sleep(30);
        writer.mark();
        assertEquals(first, response.getHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME),
                "mark() 幂等，重复调用不得改写响应头");
    }

    @Test
    void degradesSilentlyWithoutResponse() {
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(System.currentTimeMillis(), null, null);
        writer.mark();
        writer.mark();
        // 无 response 时静默跳过，不抛异常即通过
    }

    @Test
    void noopMarkIsSilent() {
        FirstTokenHeaderWriter.noop().mark();
    }

    @Test
    void ofContextWithoutRequestAttributesDoesNotThrow() {
        // 非 web 场景（无请求属性）时拿不到 response，必须降级而非炸链路
        GatewayRequestContext context = new GatewayRequestContext(
                "Bearer key", "key", "rule-code", null, null, null);
        FirstTokenHeaderWriter writer = FirstTokenHeaderWriter.of(context);
        writer.mark();
    }

    @Test
    void ofContextReadsCurrentResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(
                        new org.springframework.mock.web.MockHttpServletRequest(), response));
        GatewayRequestContext context = new GatewayRequestContext(
                "Bearer key", "key", "rule-code", null, null, null);

        FirstTokenHeaderWriter.of(context).mark();

        String ttft = response.getHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME);
        assertNotNull(ttft, "of(context) 应自动绑定当前请求的 response");
    }

    @Test
    void ofContextResetsToNoHeaderWhenResponseMissing() {
        // 再次确认无请求属性时 of(context) 产出的写头器不写任何头
        MockHttpServletResponse response = new MockHttpServletResponse();
        GatewayRequestContext context = new GatewayRequestContext(
                "Bearer key", "key", "rule-code", null, null, null);
        FirstTokenHeaderWriter.of(context).mark();
        assertNull(response.getHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME));
    }

    @Test
    void observerFiresExactlyOnce() throws InterruptedException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(
                System.currentTimeMillis(), response, value -> calls.incrementAndGet());

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
                System.currentTimeMillis() - 120L, null, elapsed::set);

        writer.mark();

        assertTrue(elapsed.get() >= 0L, "无 response 时观察者仍必须触发（初值 -1）");
        assertTrue(elapsed.get() >= 120L, "观察者应收到端到端耗时，实际 " + elapsed.get() + "ms");
    }

    @Test
    void observerReceivesElapsedBeforeHeaderWrite() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicLong observed = new AtomicLong(-1L);
        AtomicInteger headerAtObserverTime = new AtomicInteger();
        FirstTokenHeaderWriter writer = new FirstTokenHeaderWriter(
                System.currentTimeMillis() - 50L, response, value -> {
                    observed.set(value);
                    // 观察者触发时响应头尚未写入（先通知后写头）
                    headerAtObserverTime.set(response.getHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME) == null ? 0 : 1);
                });

        writer.mark();

        assertTrue(observed.get() >= 50L, "观察者应收到耗时，实际 " + observed.get() + "ms");
        assertEquals(0, headerAtObserverTime.get(), "观察者必须先于写头触发");
        assertNotNull(response.getHeader(GatewayHeaders.X_KNOT_FIRST_TOKEN_TIME), "写头仍须完成");
    }
}
