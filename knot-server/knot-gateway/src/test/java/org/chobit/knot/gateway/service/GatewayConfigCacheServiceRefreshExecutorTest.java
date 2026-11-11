package org.chobit.knot.gateway.service;

import com.github.benmanes.caffeine.cache.LoadingCache;
import org.chobit.knot.svc.service.GatewayConfigQueryService;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 守门：{@code refreshAfterWrite} 的异步重载<b>必须落在注入的执行器上</b>。
 *
 * <p>删除 {@code builder()} 里的 {@code .executor(refreshExecutor)} 后，Caffeine 会退回
 * {@code ForkJoinPool.commonPool()}——测试里这个记录型执行器就收不到任务，本用例立刻变红。
 * 这就是本用例存在的唯一理由：那行代码本身没有任何编译期或运行期的其它症状。</p>
 *
 * <p>用 {@code LoadingCache#refresh(key)} 触发：它是同一条异步刷新路径（同样交给配置的执行器），
 * 但不必等 {@code refreshAfterWrite}（30 秒，且是 private 常量，测试无法缩短）。</p>
 */
class GatewayConfigCacheServiceRefreshExecutorTest {

    @Test
    void refreshIsSubmittedToInjectedExecutorInsteadOfCallerThread() throws Exception {
        CountingQuery query = new CountingQuery();
        RecordingExecutor executor = new RecordingExecutor();
        GatewayConfigCacheService service = newService(query, executor);

        assertNull(service.getModelById(1L));
        assertEquals(1, query.modelByIdLoads.get(), "首次读是 miss，应就地回源一次");
        assertSame(Thread.currentThread(), query.lastLoadThread, "miss 回源发生在调用线程");

        modelByIdCache(service).refresh(1L);

        // 先判确定性判据：不配 .executor(...) 时任务全落 commonPool，本记录器一条都收不到。
        // 断言「至少收到任务」而不是精确条数：Caffeine 的写后维护任务也走同一个执行器。
        assertFalse(executor.tasks.isEmpty(), "刷新任务必须交给注入的执行器");
        assertEquals(1, query.modelByIdLoads.get(), "refresh 是异步的，不能就地回源");

        executor.runOnExecutorThread();
        assertEquals(2, query.modelByIdLoads.get(), "执行器真正跑起来才回源");
        assertEquals("recording-executor", query.lastLoadThread.getName(),
                "重载必须发生在执行器线程；若退回 commonPool，这里会是 ForkJoinPool.commonPool-worker");
    }

    @Test
    void nullExecutorIsRejectedAtConstruction() {
        // 不 fail-fast 的话，空执行器要到 30 秒后第一次后台刷新才炸，且炸在别的线程上
        assertThrows(NullPointerException.class, () -> new GatewayConfigCacheService(
                new CountingQuery(),
                new ResourceTrafficPolicySupport(null, null, null, null),
                null));
    }

    private static GatewayConfigCacheService newService(CountingQuery query, Executor executor) {
        return new GatewayConfigCacheService(query,
                new ResourceTrafficPolicySupport(null, null, null, null),
                executor);
    }

    @SuppressWarnings("unchecked")
    private static LoadingCache<Object, Object> modelByIdCache(GatewayConfigCacheService service)
            throws ReflectiveOperationException {
        Field field = GatewayConfigCacheService.class.getDeclaredField("modelByIdCache");
        field.setAccessible(true);
        return (LoadingCache<Object, Object>) field.get(service);
    }

    /** 只统计回源次数与回源线程：构造器是纯赋值（无 requireNonNull、无方法引用），可全传 null 后覆写读方法。 */
    private static final class CountingQuery extends GatewayConfigQueryService {

        private final AtomicInteger modelByIdLoads = new AtomicInteger();
        private volatile Thread lastLoadThread;

        private CountingQuery() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public ModelConfigVo getModelById(Long id) {
            modelByIdLoads.incrementAndGet();
            lastLoadThread = Thread.currentThread();
            return null;
        }
    }

    /**
     * 只登记不执行，留到 {@link #runOnExecutorThread()} 统一起一个具名线程跑。
     *
     * <p>专门起线程而不是就地 {@code run()}：只有换线程，「重载线程名」才能证明刷新确实
     * 经过了本执行器，而不是被打回 {@code commonPool}。</p>
     */
    private static final class RecordingExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runOnExecutorThread() throws InterruptedException {
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            Thread worker = new Thread(() -> {
                try {
                    pending.forEach(Runnable::run);
                } catch (Throwable e) {
                    failure.set(e);
                }
            }, "recording-executor");
            worker.start();
            worker.join(2_000);
            assertNull(failure.get(), "刷新任务不应抛错");
        }
    }
}
