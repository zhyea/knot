package org.chobit.knot.gateway.moderation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.chobit.knot.common.moderation.ModerationTerm;
import org.chobit.knot.gateway.metrics.GatewayMetrics;
import org.chobit.knot.gateway.service.support.ModerationWordResource;
import org.chobit.knot.svc.service.ModerationWordService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关侧词库资源缓存：加载 / 缓存复用 / fail-open / 失败不缓存。
 *
 * <p>JDK25 Mockito inline 不能 mock 具体类（Byte Buddy 无法 instrument 类层级），
 * 按项目先例用 {@link ModerationWordService} 子类覆写取词。</p>
 */
class ModerationWordResourceTest {

    /** 计数桩：可返回固定词表、返回 null 或抛异常 */
    private static final class StubWordService extends ModerationWordService {

        private final AtomicInteger calls = new AtomicInteger();
        private final List<ModerationTerm> terms;
        private final RuntimeException failure;

        private StubWordService(List<ModerationTerm> terms, RuntimeException failure) {
            super(null, null);
            this.terms = terms;
            this.failure = failure;
        }

        @Override
        public List<ModerationTerm> loadEnabledTerms() {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return terms;
        }
    }

    private static final List<ModerationTerm> TERMS = List.of(
            ModerationTerm.of("badword", "badword", "POLITICS", 2, 1_700_000_000_000L),
            ModerationTerm.of("spam", "spam", "AD", 1, 1_700_000_001_000L));

    /** 每例独立 registry：JUnit 默认 PER_METHOD，计数不会跨用例污染 */
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private ModerationWordResource newResource(ModerationWordService service) {
        return new ModerationWordResource(service, registry);
    }

    /**
     * 守门：fail-open 必须同时出现在指标里，不能只有日志和进程内计数器。
     *
     * <p>删掉 catch 里的 {@code failOpenCounter.increment()} → 本用例红；
     * 把两个 gauge 的注册删掉 → 取值抛 {@code MeterNotFoundException}。</p>
     */
    @Test
    void failOpenIsVisibleAsMetrics() {
        StubWordService service = new StubWordService(null, new IllegalStateException("db down"));
        ModerationWordResource resource = newResource(service);

        assertTrue(resource.currentWords().isEmpty());
        assertEquals(1.0, registry.get(GatewayMetrics.MODERATION_FAIL_OPEN).counter().count());
        assertEquals(1.0, registry.get(GatewayMetrics.MODERATION_FAIL_OPEN_ACTIVE).gauge().value());
        // 从未成功取过词，规模保持 0
        assertEquals(0.0, registry.get(GatewayMetrics.MODERATION_WORD_COUNT).gauge().value());

        // 第二次 fail-open 继续累加（单调计数才能被 rate() 用）
        assertTrue(resource.currentWords().isEmpty());
        assertEquals(2.0, registry.get(GatewayMetrics.MODERATION_FAIL_OPEN).counter().count());
    }

    @Test
    void loadsEnabledTermsOnFirstRead() {
        StubWordService service = new StubWordService(TERMS, null);
        ModerationWordResource resource = newResource(service);

        List<ModerationTerm> words = resource.currentWords();

        assertEquals(2, words.size());
        assertEquals("badword", words.get(0).word());
        assertEquals(1, service.calls.get());
    }

    @Test
    void cachesWithinTtlSoLoaderRunsOnce() {
        StubWordService service = new StubWordService(TERMS, null);
        ModerationWordResource resource = newResource(service);

        List<ModerationTerm> first = resource.currentWords();
        List<ModerationTerm> second = resource.currentWords();
        List<ModerationTerm> third = resource.currentWords();

        // 同一请求三个插件 stage 反复取词只打一次桩：缓存生效
        assertSame(first, second);
        assertSame(second, third);
        assertEquals(1, service.calls.get());
    }

    @Test
    void normalizesNullToEmptyList() {
        StubWordService service = new StubWordService(null, null);
        ModerationWordResource resource = newResource(service);

        assertTrue(resource.currentWords().isEmpty());
    }

    @Test
    void failsOpenWithEmptyListWhenLoadThrows() {
        StubWordService service = new StubWordService(null, new IllegalStateException("db down"));
        ModerationWordResource resource = newResource(service);

        assertTrue(resource.currentWords().isEmpty());
    }

    @Test
    void retriesLoadAfterFailureInsteadOfCachingFailure() {
        StubWordService service = new StubWordService(null, new IllegalStateException("db down"));
        ModerationWordResource resource = newResource(service);

        assertTrue(resource.currentWords().isEmpty());
        assertTrue(resource.currentWords().isEmpty());

        // 失败不缓存：每次取词都重新加载，恢复后自行自愈
        assertEquals(2, service.calls.get());
    }
}
