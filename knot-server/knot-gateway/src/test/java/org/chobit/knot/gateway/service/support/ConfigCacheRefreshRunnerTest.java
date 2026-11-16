package org.chobit.knot.gateway.service.support;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.chobit.knot.gateway.config.ConfigCacheDomain;
import org.chobit.knot.gateway.config.ConfigRefreshProperties;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.svc.model.ConfigTableRevision;
import org.chobit.knot.svc.service.GatewayConfigQueryService;
import org.chobit.knot.svc.service.support.ConfigRevisionQuerySupport;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编排契约：何时建基线、何时失效、失败与缺表怎么处理。
 *
 * <p>全部用子类覆写而不是 mock——{@code ConfigRevisionQuerySupport} / {@code GatewayConfigCacheService}
 * 都是具体类，JDK 25 下 Mockito 不能 mock 具体类。</p>
 */
class ConfigCacheRefreshRunnerTest {

    @Test
    void firstTickOnlyEstablishesBaseline() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_apps", 1, "a"));

        fixture.runner.tick();

        assertTrue(fixture.cache.invalidated.isEmpty(), "首个节拍只建基线，不失效");
    }

    @Test
    void unchangedFingerprintDoesNotInvalidate() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_apps", 1, "a"));
        fixture.runner.tick();

        fixture.runner.tick();

        assertTrue(fixture.cache.invalidated.isEmpty());
    }

    @Test
    void changedTableInvalidatesItsWholeDomain() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_apps", 1, "a"), row("kb_models", 1, "a"));
        fixture.runner.tick();

        fixture.query.rows = List.of(row("kb_apps", 1, "a"), row("kb_models", 1, "b"));
        fixture.runner.tick();

        assertEquals(EnumSet.of(ConfigCacheDomain.MODEL), fixture.cache.invalidated);
    }

    @Test
    void rowCountChangeIsDetected() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_apps", 2, "a"));
        fixture.runner.tick();

        fixture.query.rows = List.of(row("kb_apps", 1, "a"));
        fixture.runner.tick();

        assertEquals(EnumSet.of(ConfigCacheDomain.APP), fixture.cache.invalidated,
                "物理删除不会改变 updated_at，必须靠行数捕获");
    }

    @Test
    void wordTableChangeGoesToWordResource() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_moderation_word_lists", 1, "a"));
        fixture.runner.tick();

        fixture.query.rows = List.of(row("kb_moderation_word_lists", 2, "a"));
        fixture.runner.tick();

        assertEquals(1, fixture.words.invalidations);
        assertTrue(fixture.cache.invalidated.isEmpty(), "词库副本不在配置缓存类内");
    }

    @Test
    void probeFailureKeepsCacheAndDoesNotAdvanceBaseline() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_apps", 1, "a"));
        fixture.runner.tick();

        fixture.query.failure = new IllegalStateException("db down");
        assertDoesNotThrow(fixture.runner::tick);
        assertTrue(fixture.cache.invalidated.isEmpty(), "探测失败只记 ERROR，不动缓存");

        fixture.query.failure = null;
        fixture.query.rows = List.of(row("kb_apps", 2, "a"));
        fixture.runner.tick();

        assertEquals(EnumSet.of(ConfigCacheDomain.APP), fixture.cache.invalidated,
                "失败不推进基线，恢复后仍能比出真实变化");
    }

    @Test
    void emptySnapshotSkipsTick() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_apps", 1, "a"));
        fixture.runner.tick();

        fixture.query.rows = List.of();
        fixture.runner.tick();

        assertTrue(fixture.cache.invalidated.isEmpty());
    }

    @Test
    void tableDisappearingFromSnapshotInvalidatesOnce() {
        Fixture fixture = new Fixture();
        fixture.query.rows = List.of(row("kb_apps", 1, "a"), row("kb_models", 1, "a"));
        fixture.runner.tick();

        fixture.query.rows = List.of(row("kb_apps", 1, "a"));
        fixture.runner.tick();
        fixture.runner.tick();

        assertEquals(EnumSet.of(ConfigCacheDomain.MODEL), fixture.cache.invalidated,
                "从有到缺失效一次；持续缺失不得每拍重复失效");
    }

    private static ConfigTableRevision row(String table, long rowCount, String revision) {
        return new ConfigTableRevision(table, rowCount, revision);
    }

    private static final class Fixture {

        private final StubQuery query = new StubQuery();
        private final RecordingCache cache = new RecordingCache();
        private final RecordingWordResource words = new RecordingWordResource();
        private final ConfigCacheRefreshRunner runner =
                new ConfigCacheRefreshRunner(query, cache, words, new ConfigRefreshProperties());
    }

    private static final class StubQuery extends ConfigRevisionQuerySupport {

        private List<ConfigTableRevision> rows = List.of();
        private RuntimeException failure;

        private StubQuery() {
            super(null);
        }

        @Override
        public List<ConfigTableRevision> snapshot() {
            if (failure != null) {
                throw failure;
            }
            return rows;
        }
    }

    private static final class RecordingCache extends GatewayConfigCacheService {

        private final Set<ConfigCacheDomain> invalidated = EnumSet.noneOf(ConfigCacheDomain.class);

        private RecordingCache() {
            super(new EmptyQuery(), new ResourceTrafficPolicySupport(null, null, null, null), Runnable::run);
        }

        @Override
        public void invalidate(Collection<ConfigCacheDomain> domains) {
            invalidated.addAll(domains);
        }
    }

    private static final class RecordingWordResource extends ModerationWordResource {

        private int invalidations;

        private RecordingWordResource() {
            super(null, new SimpleMeterRegistry());
        }

        @Override
        public void invalidate() {
            invalidations++;
        }
    }

    private static final class EmptyQuery extends GatewayConfigQueryService {

        private EmptyQuery() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
