package org.chobit.knot.gateway.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阻塞段的调度切换 + MDC 重绑（{@link MdcContextSupport}）。
 *
 * <p>两条不变量：</p>
 * <ol>
 *   <li>阻塞任务<b>不在提交线程上执行</b>——切 Netty 后提交线程可能就是 event loop，
 *       任务回落过去会把 I/O 线程卡死；</li>
 *   <li>任务线程上的业务日志必须带 traceId（MDC 是线程本地变量，跨线程不会自己传播）。</li>
 * </ol>
 */
class MdcContextSupportTest {

    private static final String INCOMING = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("任务线程上 MDC 已绑定 traceId/spanId，且与 Reactor Context 一致")
    void bindsMdcOnTaskThread() {
        GatewayTraceContext trace = GatewayTraceContext.fromTraceparent(INCOMING);
        AtomicReference<String> taskThread = new AtomicReference<>();
        AtomicReference<String> traceIdOnTask = new AtomicReference<>();
        AtomicReference<String> spanIdOnTask = new AtomicReference<>();
        AtomicReference<String> traceparentOnTask = new AtomicReference<>();

        Mono.<String>fromCallable(() -> {
            taskThread.set(Thread.currentThread().getName());
            traceIdOnTask.set(MDC.get(GatewayTraceContext.MDC_TRACE_ID));
            spanIdOnTask.set(MDC.get(GatewayTraceContext.MDC_SPAN_ID));
            traceparentOnTask.set(MDC.get(GatewayTraceContext.MDC_TRACEPARENT));
            return "done";
        })
                .transform(work -> MdcContextSupport.onBlockingScheduler(work, Schedulers.boundedElastic()))
                .contextWrite(context -> context.put(GatewayTraceContext.CONTEXT_KEY, trace))
                .block();

        assertEquals(trace.traceId(), traceIdOnTask.get(), "任务线程日志必须带 traceId");
        assertEquals(trace.spanId(), spanIdOnTask.get(), "任务线程日志必须带 spanId");
        assertEquals(trace.traceparent(), traceparentOnTask.get());
        assertNotEquals(Thread.currentThread().getName(), taskThread.get(), "阻塞任务不得在提交线程上执行");
    }

    @Test
    @DisplayName("提交线程的 MDC 不被污染")
    void doesNotLeakIntoSubmittingThread() {
        GatewayTraceContext trace = GatewayTraceContext.fromTraceparent(INCOMING);

        Mono.fromCallable(() -> "x")
                .transform(work -> MdcContextSupport.onBlockingScheduler(work, Schedulers.boundedElastic()))
                .contextWrite(context -> context.put(GatewayTraceContext.CONTEXT_KEY, trace))
                .block();

        assertNull(MDC.get(GatewayTraceContext.MDC_TRACE_ID), "MDC 只在任务线程上绑，不能留在提交线程");
    }

    @Test
    @DisplayName("无追踪上下文（单测/非 web 入口）时只切调度，不绑定也不炸")
    void degradesWithoutTraceContext() {
        AtomicReference<String> traceIdOnTask = new AtomicReference<>("unset");

        String result = Mono.fromCallable(() -> {
            traceIdOnTask.set(MDC.get(GatewayTraceContext.MDC_TRACE_ID));
            return "ok";
        })
                .transform(work -> MdcContextSupport.onBlockingScheduler(work, Schedulers.boundedElastic()))
                .block();

        assertEquals("ok", result);
        assertNull(traceIdOnTask.get(), "无上下文时不写 MDC");
    }

    @Test
    @DisplayName("未注入调度器时退化为共享 boundedElastic，仍然离开提交线程")
    void fallsBackToSharedBoundedElastic() {
        GatewayTraceContext trace = GatewayTraceContext.fromTraceparent(INCOMING);
        AtomicReference<String> taskThread = new AtomicReference<>();

        Mono.fromCallable(() -> {
            taskThread.set(Thread.currentThread().getName());
            return MDC.get(GatewayTraceContext.MDC_TRACE_ID);
        })
                .transform(work -> MdcContextSupport.onBlockingScheduler(work, null))
                .contextWrite(context -> context.put(GatewayTraceContext.CONTEXT_KEY, trace))
                .block();

        assertTrue(taskThread.get() != null && !taskThread.get().equals(Thread.currentThread().getName()),
                "兜底调度器同样必须换线程");
    }
}
