package org.chobit.knot.gateway.moderation;

import org.chobit.knot.gateway.service.ModerationWordResource;
import org.chobit.knot.gateway.service.ModerationWordService;
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

    @Test
    void loadsEnabledTermsOnFirstRead() {
        StubWordService service = new StubWordService(TERMS, null);
        ModerationWordResource resource = new ModerationWordResource(service);

        List<ModerationTerm> words = resource.currentWords();

        assertEquals(2, words.size());
        assertEquals("badword", words.get(0).word());
        assertEquals(1, service.calls.get());
    }

    @Test
    void cachesWithinTtlSoLoaderRunsOnce() {
        StubWordService service = new StubWordService(TERMS, null);
        ModerationWordResource resource = new ModerationWordResource(service);

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
        ModerationWordResource resource = new ModerationWordResource(service);

        assertTrue(resource.currentWords().isEmpty());
    }

    @Test
    void failsOpenWithEmptyListWhenLoadThrows() {
        StubWordService service = new StubWordService(null, new IllegalStateException("db down"));
        ModerationWordResource resource = new ModerationWordResource(service);

        assertTrue(resource.currentWords().isEmpty());
    }

    @Test
    void retriesLoadAfterFailureInsteadOfCachingFailure() {
        StubWordService service = new StubWordService(null, new IllegalStateException("db down"));
        ModerationWordResource resource = new ModerationWordResource(service);

        assertTrue(resource.currentWords().isEmpty());
        assertTrue(resource.currentWords().isEmpty());

        // 失败不缓存：每次取词都重新加载，恢复后自行自愈
        assertEquals(2, service.calls.get());
    }
}
