package org.chobit.knot.gateway.plugin.moderation;

import org.chobit.knot.gateway.plugin.refresh.ScheduledRefreshManager;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationSnapshotCacheTest {

    private static ModerationTerm term(String word, int severity, long updatedAtMillis) {
        return new ModerationTerm(word, TextNormalizer.normalize(word), "ABUSE", severity, updatedAtMillis);
    }

    /**
     * 可变词库：版本号随词条变化，用于验证轮询失效。
     */
    private static final class MutableSource implements ModerationWordSource {

        private final AtomicInteger loadCount = new AtomicInteger();
        private volatile List<ModerationTerm> terms = List.of();

        void setTerms(List<ModerationTerm> terms) {
            this.terms = terms;
        }

        @Override
        public List<ModerationTerm> loadTerms() {
            loadCount.incrementAndGet();
            return terms;
        }

        @Override
        public long currentVersion() {
            return terms.stream()
                    .map(ModerationTerm::updatedAtMillis)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
        }
    }

    @Test
    void loadsOnceWhenVersionUnchanged() {
        MutableSource source = new MutableSource();
        source.setTerms(List.of(term("badword", 2, 1_000L)));
        ModerationSnapshotCache cache = new ModerationSnapshotCache(source, 0L);

        ModerationSnapshot first = cache.snapshot();
        ModerationSnapshot second = cache.snapshot();

        assertSame(first, second);
        assertEquals(1, source.loadCount.get(), "版本未变时不应重复加载词库");
    }

    @Test
    void reloadsWhenVersionChanged() {
        MutableSource source = new MutableSource();
        source.setTerms(List.of(term("badword", 2, 1_000L)));
        ModerationSnapshotCache cache = new ModerationSnapshotCache(source, 0L);
        ModerationSnapshot before = cache.snapshot();
        assertEquals(1_000L, before.version());

        source.setTerms(List.of(term("badword", 2, 2_000L), term("worse", 3, 2_000L)));
        ModerationSnapshot after = cache.snapshot();

        assertEquals(2_000L, after.version());
        assertEquals(2, source.loadCount.get());
    }

    @Test
    void reloadsWhenWordListBecomesEmpty() {
        MutableSource source = new MutableSource();
        source.setTerms(List.of(term("badword", 2, 1_000L)));
        ModerationSnapshotCache cache = new ModerationSnapshotCache(source, 0L);
        cache.snapshot();

        source.setTerms(List.of());
        ModerationSnapshot after = cache.snapshot();

        assertTrue(after.empty(), "词库清空后应得到空快照");
    }

    @Test
    void keepsSnapshotWhenVersionProbeFails() {
        MutableSource source = new MutableSource();
        source.setTerms(List.of(term("badword", 2, 1_000L)));
        ModerationSnapshotCache cache = new ModerationSnapshotCache(source, 0L);
        ModerationSnapshot before = cache.snapshot();

        ModerationSnapshot after = cache.snapshot();

        assertSame(before, after);
    }

    @Test
    void emptyWordListYieldsEmptySnapshot() {
        ModerationSnapshotCache cache = new ModerationSnapshotCache(new MutableSource(), 0L);

        assertTrue(cache.snapshot().empty());
    }

    @Test
    void invalidateForcesReload() {
        MutableSource source = new MutableSource();
        source.setTerms(List.of(term("badword", 2, 1_000L)));
        ModerationSnapshotCache cache = new ModerationSnapshotCache(source, 0L);
        cache.snapshot();

        cache.invalidate();
        cache.snapshot();

        assertEquals(2, source.loadCount.get());
    }

    @Test
    void versionProbeFailureDoesNotBreakSnapshot() {
        ModerationWordSource failing = new ModerationWordSource() {
            @Override
            public List<ModerationTerm> loadTerms() {
                return List.of(term("badword", 2, 1_000L));
            }

            @Override
            public long currentVersion() {
                throw new IllegalStateException("db down");
            }
        };
        ModerationSnapshotCache cache = new ModerationSnapshotCache(failing, 0L);
        ModerationSnapshot first = cache.snapshot();

        assertSame(first, cache.snapshot());
        assertThrows(IllegalStateException.class, failing::currentVersion);
    }

    /**
     * 验证定时刷新能绕过 max(updated_at) 版本盲区：删除非最新行时版本号不变，
     * 纯版本轮询不会失效，只能靠定时直读词表兜底。
     */
    @Test
    void scheduledRefreshPicksUpDeletedNonLatestWord() throws Exception {
        MutableSource source = new MutableSource();
        // A 是较旧行(updatedAt=1000)，B 是最新行(updatedAt=2000)
        source.setTerms(List.of(term("A", 2, 1_000L), term("B", 2, 2_000L)));
        // 极短刷新间隔，验证定时刷新能绕过版本盲区（经通用托管器 ScheduledRefreshManager 调度）
        ModerationSnapshotCache cache = new ModerationSnapshotCache(source, 0L, Duration.ofMillis(50));
        ScheduledRefreshManager manager = new ScheduledRefreshManager(List.of(cache));
        manager.start();
        try {
            ModerationSnapshot first = cache.snapshot();
            assertEquals(1, first.matcher().scan("a").size(), "初始 A 应被命中");

            // 删除非最新行 A：max(updated_at) 仍是 B 的 2000，版本号不变 → 纯版本轮询不会失效
            source.setTerms(List.of(term("B", 2, 2_000L)));

            boolean caught = false;
            for (int i = 0; i < 40; i++) { // 最多约 2s
                Thread.sleep(50);
                ModerationSnapshot current = cache.snapshot();
                if (current.matcher().scan("a").isEmpty()) {
                    assertEquals(1, current.matcher().scan("b").size(), "B 仍应被命中");
                    caught = true;
                    break;
                }
            }
            assertTrue(caught, "定时刷新应在周期内拾取删除的非最新词 A（绕过版本盲区）");
        } finally {
            manager.shutdown();
        }
    }
}
