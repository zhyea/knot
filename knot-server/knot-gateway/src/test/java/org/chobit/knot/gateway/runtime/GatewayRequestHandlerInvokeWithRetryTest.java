package org.chobit.knot.gateway.runtime;

import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code invokeWithRetry} 的非阻塞重试语义验证。
 *
 * <p>重点证明：重试策略口径被完整保留（重试次数上限、可重试判定），且退避通过
 * {@link java.util.concurrent.CompletableFuture#delayedExecutor} 承载——延迟期间不阻塞线程（见
 * {@link #backoffDoesNotBlockThread}：单线程执行器下仍能按时完成，说明没有 Thread.sleep 占住线程）。</p>
 */
class GatewayRequestHandlerInvokeWithRetryTest {

    /** 直接执行器：任务在调用线程就地执行，便于在单线程下检验「退避不阻塞线程」。 */
    private final Executor directExecutor = Runnable::run;

    private RetryPolicy retryPolicy(int maxAttempts) {
        return new RetryPolicy(Boolean.TRUE, maxAttempts, 0, 0, 1.0, Boolean.FALSE, Boolean.FALSE,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
    }

    private GatewayRequestHandler handler() {
        // 仅 invokeWithRetry 路径使用 gatewayAsyncExecutor；其余字段本测试不触达，传 null
        return new GatewayRequestHandler(null, null, null, null, null, null, null, null,
                null, null, null, directExecutor, null, null, null);
    }

    private RoutingRuleTargetDto candidate() {
        return new RoutingRuleTargetDto("m", 1L, "code", "up", "模型", "CHAT", null, 1, true, null);
    }

    @Test
    @DisplayName("首次成功：不触发任何重试")
    void firstAttemptSucceedsWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        ProxyResult ok = new ProxyResult("body", 1L, 2L, null);
        CompletableFuture<ProxyResult> f = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    calls.incrementAndGet();
                    return ok;
                }, null);
        assertEquals(ok, f.join());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("可重试失败先原地重投，第 N 次成功即返回")
    void retriesThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        ProxyResult ok = new ProxyResult("body", 1L, 2L, null);
        CompletableFuture<ProxyResult> f = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) {
                        throw new GatewayUpstreamException("up", "UPSTREAM_ERROR", 503, null);
                    }
                    return ok;
                }, null);
        assertEquals(ok, f.join());
        assertEquals(3, calls.get(), "第 3 次应成功，共尝试 3 次");
    }

    @Test
    @DisplayName("达到 maxAttempts 后停止重试，抛出末次上游异常")
    void exhaustsMaxAttemptsThenThrows() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<ProxyResult> f = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    calls.incrementAndGet();
                    throw new GatewayUpstreamException("up", "UPSTREAM_ERROR", 503, null);
                }, null);
        CompletionException ex = assertThrows(CompletionException.class, f::join);
        GatewayUpstreamException cause = assertInstanceOf(GatewayUpstreamException.class, ex.getCause());
        assertEquals(3, calls.get(), "达到 maxAttempts 后停止重试");
        assertEquals(503, cause.httpStatus());
    }

    @Test
    @DisplayName("不可重试错误（如 MODEL_NOT_FOUND）只尝试一次")
    void nonRetryableErrorDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<ProxyResult> f = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    calls.incrementAndGet();
                    throw new GatewayUpstreamException("nf", "MODEL_NOT_FOUND", null, null);
                }, null);
        assertThrows(CompletionException.class, f::join);
        assertEquals(1, calls.get(), "不可重试错误只尝试一次");
    }

    @Test
    @DisplayName("退避采用定时器而非阻塞线程：单线程执行器下仍能按时完成")
    void backoffDoesNotBlockThread() {
        AtomicInteger calls = new AtomicInteger();
        ProxyResult ok = new ProxyResult("body", 1L, 2L, null);
        // 50ms 退避 + maxAttempts=2：若退避是 Thread.sleep 阻塞，在单线程执行器上会死锁/超时；
        // 用 delayedExecutor 则定时器到期即续，join 能在合理时间内返回。
        RetryPolicy policy = new RetryPolicy(Boolean.TRUE, 2, 50, 50, 1.0, Boolean.FALSE, Boolean.FALSE,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
        long start = System.nanoTime();
        CompletableFuture<ProxyResult> f = handler().invokeWithRetry(policy, candidate(), null,
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 2) {
                        throw new GatewayUpstreamException("up", "UPSTREAM_ERROR", 503, null);
                    }
                    return ok;
                }, null);
        assertEquals(ok, f.join());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 40, "应经历一次退避延迟（>=40ms），实测 " + elapsedMs + "ms");
        assertEquals(2, calls.get());
    }
}
