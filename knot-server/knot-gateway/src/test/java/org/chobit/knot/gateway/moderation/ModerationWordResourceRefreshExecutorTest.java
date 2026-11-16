package org.chobit.knot.gateway.moderation;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.chobit.knot.common.moderation.ModerationTerm;
import org.chobit.knot.gateway.service.support.ModerationWordResource;
import org.chobit.knot.svc.service.ModerationWordService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守门：词库缓存的 {@code refreshAfterWrite} 重载<b>必须落在注入的执行器上</b>。
 *
 * <p>loader 是阻塞的 MyBatis 查询。删掉 {@code builder()} 里的 {@code .executor(...)} 后，
 * Caffeine 会退回 {@code ForkJoinPool.commonPool()}——那个池并行度等于 CPU 核数，
 * 一次慢查询会拖住同窗口内所有 commonPool 使用者。本用例里的记录型执行器届时收不到任务，
 * 用例立刻变红；那行代码本身没有任何其它编译期 / 运行期症状。</p>
 *
 * <p>用 {@code LoadingCache#refresh(key)} 触发：它与 {@code refreshAfterWrite} 是同一条异步刷新路径，
 * 但不必等 30 秒（{@code REFRESH_AFTER_WRITE} 是 private 常量，测试无法缩短）。</p>
 */
class ModerationWordResourceRefreshExecutorTest {

    @Test
    void refreshIsSubmittedToInjectedExecutorInsteadOfCallerThread() throws Exception {
        CountingWordService service = new CountingWordService();
        RecordingExecutor executor = new RecordingExecutor();
        ModerationWordResource resource = new ModerationWordResource(
                service, new SimpleMeterRegistry(), executor);

        assertTrue(resource.currentWords().isEmpty());
        assertEquals(1, service.calls.get(), "首次读是 miss，应就地回源一次");
        assertSame(Thread.currentThread(), service.lastThread, "miss 回源发生在调用线程");

        enabledCache(resource).refresh("enabled");

        // 先判确定性判据：不配 .executor(...) 时任务全落 commonPool，本记录器一条都收不到
        assertFalse(executor.tasks.isEmpty(), "刷新任务必须交给注入的执行器");
        assertEquals(1, service.calls.get(), "refresh 是异步的，不能就地回源");

        executor.runOnExecutorThread();
        assertEquals(2, service.calls.get(), "执行器真正跑起来才回源");
        assertEquals("recording-executor", service.lastThread.getName(),
                "重载必须发生在执行器线程；退回 commonPool 时这里会是 ForkJoinPool.commonPool-worker");
    }

    @Test
    void nullExecutorIsRejectedAtConstruction() {
        // 不 fail-fast 的话，空执行器要等第一次后台刷新才炸，而且炸在别的线程上
        assertThrows(NullPointerException.class, () -> new ModerationWordResource(
                new CountingWordService(), new SimpleMeterRegistry(), null));
    }

    @SuppressWarnings("unchecked")
    private static LoadingCache<String, List<ModerationTerm>> enabledCache(ModerationWordResource resource)
            throws ReflectiveOperationException {
        Field field = ModerationWordResource.class.getDeclaredField("cache");
        field.setAccessible(true);
        return (LoadingCache<String, List<ModerationTerm>>) field.get(resource);
    }

    /** 只统计回源次数与回源线程：构造器纯赋值，可全传 null 后覆写取词。 */
    private static final class CountingWordService extends ModerationWordService {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile Thread lastThread;

        private CountingWordService() {
            super(null, null);
        }

        @Override
        public List<ModerationTerm> loadEnabledTerms() {
            calls.incrementAndGet();
            lastThread = Thread.currentThread();
            return List.of();
        }
    }

    /**
     * 只登记不执行，留到 {@link #runOnExecutorThread()} 起一个具名线程统一跑。
     * 起线程而不是就地 {@code run()}：只有换线程，「重载线程名」才能证明刷新确实经过本执行器。
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
