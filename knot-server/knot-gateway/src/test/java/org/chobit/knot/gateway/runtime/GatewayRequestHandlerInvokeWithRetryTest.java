package org.chobit.knot.gateway.runtime;

import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.common.model.RetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code invokeWithRetry} 的非阻塞重试语义验证。
 *
 * <p>重点证明：重试策略口径被完整保留（重试次数上限、可重试判定），且退避由 Reactor 的
 * 定时调度器承载——延迟期间不阻塞线程（见 {@link #backoffDoesNotBlockThread()}：
 * 单线程执行器下仍能按时完成，说明没有 {@code Thread.sleep} 占住线程）。</p>
 *
 * <p>2026-10-20（WebFlux 批1-B）：返回值由 {@code CompletableFuture} 改为 {@link Mono}，
 * 断言方式同步改为 {@code block()} / {@code assertThrows}。</p>
 */
class GatewayRequestHandlerInvokeWithRetryTest {

    /**
     * 立即调度器：任务在订阅线程就地执行，便于在单线程下检验「退避不阻塞线程」
     * （与旧实现的 {@code Runnable::run} 直接执行器同义）。
     */
    private final Scheduler directScheduler = Schedulers.immediate();

    private RetryPolicy retryPolicy(int maxAttempts) {
        return new RetryPolicy(Boolean.TRUE, maxAttempts, 0, 0, 1.0, Boolean.FALSE, Boolean.FALSE,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
    }

    private GatewayRequestHandler handler() {
        // 仅 invokeWithRetry 路径使用 blockingScheduler；其余字段本测试不触达，传 null
        return new GatewayRequestHandler(null, null, null, null, null, null, null, null,
                null, null, null, null, null, directScheduler);
    }

    private RoutingRuleTargetDto candidate() {
        return new RoutingRuleTargetDto("m", 1L, "code", "up", "模型", "CHAT", null, 1, true, null);
    }

    @Test
    @DisplayName("首次成功：不触发任何重试")
    void firstAttemptSucceedsWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        ProxyResult ok = new ProxyResult("body", 1L, 2L, null);
        Mono<ProxyResult> mono = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    calls.incrementAndGet();
                    return ok;
                });
        assertEquals(ok, mono.block());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("可重试失败先原地重投，第 N 次成功即返回")
    void retriesThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        ProxyResult ok = new ProxyResult("body", 1L, 2L, null);
        Mono<ProxyResult> mono = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) {
                        throw new GatewayUpstreamException("up", "UPSTREAM_ERROR", 503, null);
                    }
                    return ok;
                });
        assertEquals(ok, mono.block());
        assertEquals(3, calls.get(), "第 3 次应成功，共尝试 3 次");
    }

    @Test
    @DisplayName("达到 maxAttempts 后停止重试，抛出末次上游异常")
    void exhaustsMaxAttemptsThenThrows() {
        AtomicInteger calls = new AtomicInteger();
        Mono<ProxyResult> mono = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    calls.incrementAndGet();
                    throw new GatewayUpstreamException("up", "UPSTREAM_ERROR", 503, null);
                });
        GatewayUpstreamException cause = assertThrows(GatewayUpstreamException.class, mono::block);
        assertEquals(3, calls.get(), "达到 maxAttempts 后停止重试");
        assertEquals(503, cause.httpStatus());
    }

    @Test
    @DisplayName("不可重试错误（如 MODEL_NOT_FOUND）只尝试一次")
    void nonRetryableErrorDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        Mono<ProxyResult> mono = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    calls.incrementAndGet();
                    throw new GatewayUpstreamException("nf", "MODEL_NOT_FOUND", null, null);
                });
        assertThrows(GatewayUpstreamException.class, mono::block);
        assertEquals(1, calls.get(), "不可重试错误只尝试一次");
    }

    @Test
    @DisplayName("非上游异常（网关自身缺陷）直接透出，不重试也不被包装")
    void nonUpstreamErrorPropagatesUnwrapped() {
        AtomicInteger calls = new AtomicInteger();
        Mono<ProxyResult> mono = handler().invokeWithRetry(retryPolicy(3), candidate(), null,
                () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("boom");
                });
        IllegalStateException cause = assertThrows(IllegalStateException.class, mono::block);
        assertEquals("boom", cause.getMessage(), "网关自身异常必须原样透出，不被包装或替换");
        assertEquals(1, calls.get(), "网关自身异常不属于可重试上游失败");
    }

    @Test
    @DisplayName("退避采用定时器而非阻塞线程：单线程执行器下仍能按时完成")
    void backoffDoesNotBlockThread() {
        AtomicInteger calls = new AtomicInteger();
        ProxyResult ok = new ProxyResult("body", 1L, 2L, null);
        // 50ms 退避 + maxAttempts=2：若退避是 Thread.sleep 阻塞在唯一的执行线程上，
        // 定时器到期后无可用线程续跑，block() 只能等到超时；用 Mono.delay 则定时器到期即续。
        RetryPolicy policy = new RetryPolicy(Boolean.TRUE, 2, 50, 50, 1.0, Boolean.FALSE, Boolean.FALSE,
                RetryPolicy.MODE_ALLOWLIST, List.of("5xx"), RetryPolicy.MODE_SIMPLE);
        long start = System.nanoTime();
        Mono<ProxyResult> mono = handler().invokeWithRetry(policy, candidate(), null,
                () -> {
                    int n = calls.incrementAndGet();
                    if (n < 2) {
                        throw new GatewayUpstreamException("up", "UPSTREAM_ERROR", 503, null);
                    }
                    return ok;
                });
        assertEquals(ok, mono.block());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 40, "应经历一次退避延迟（>=40ms），实测 " + elapsedMs + "ms");
        assertEquals(2, calls.get());
    }
}
