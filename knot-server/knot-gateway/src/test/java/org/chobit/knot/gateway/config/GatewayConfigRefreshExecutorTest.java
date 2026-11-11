package org.chobit.knot.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置缓存刷新执行器的<b>契约</b>：命名、守护线程、以及<b>池满时丢弃而不是抛拒绝</b>。
 *
 * <p>第三条是重点：Caffeine 在 {@code get} 的读路径上直接 {@code executor.execute(...)} 提交刷新，
 * 若这里改成 {@code AbortPolicy}，队列打满时 {@code RejectedExecutionException} 会顺着
 * {@code afterRead} 冒到 {@code get()} 的调用方——<b>一次后台刷新失败会把请求打成 500</b>。
 * 所以「丢弃」不是省事，是这条路径上唯一安全的选择。</p>
 */
class GatewayConfigRefreshExecutorTest {

    /** 队列容量，与 {@code GatewayAsyncConfiguration} 中的 {@code new LinkedBlockingQueue<>(512)} 同源。 */
    private static final int QUEUE_CAPACITY = 512;

    private static final int POOL_SIZE = 2;

    @Test
    void threadsAreNamedAndDaemon() throws Exception {
        ExecutorService pool = new GatewayAsyncConfiguration().gatewayConfigRefreshExecutor();
        try {
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            pool.execute(() -> {
                worker.set(Thread.currentThread());
                done.countDown();
            });
            assertTrue(done.await(2, TimeUnit.SECONDS), "刷新线程应能启动");

            assertTrue(worker.get().getName().startsWith("gateway-cfg-refresh-"),
                    "线程名要能一眼认出归属，实际为 " + worker.get().getName());
            assertTrue(worker.get().isDaemon(), "守护线程：卡住的 JDBC 不该拖住 JVM 退出");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void saturationDiscardsRefreshInsteadOfThrowing() throws Exception {
        ExecutorService pool = new GatewayAsyncConfiguration().gatewayConfigRefreshExecutor();
        CountDownLatch hold = new CountDownLatch(1);
        try {
            CountDownLatch running = new CountDownLatch(POOL_SIZE);
            for (int i = 0; i < POOL_SIZE; i++) {
                pool.execute(() -> {
                    running.countDown();
                    awaitQuietly(hold);
                });
            }
            assertTrue(running.await(2, TimeUnit.SECONDS), "两个线程应被占满");

            // 队列塞满后继续提交：DiscardPolicy 下静默丢弃，绝不能抛到调用方
            for (int i = 0; i < QUEUE_CAPACITY + 16; i++) {
                pool.execute(() -> {
                });
            }
            assertFalse(pool.isShutdown(), "溢出提交不应导致池被关闭");
        } finally {
            hold.countDown();
            pool.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
