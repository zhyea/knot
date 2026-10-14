package org.chobit.knot.gateway.plugin.components;

import org.chobit.knot.gateway.moderation.ModerationTerm;
import org.chobit.knot.gateway.plugin.moderation.ModerationSnapshot;
import org.chobit.knot.gateway.plugin.moderation.PluginProperties;
import org.chobit.knot.gateway.util.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 词库编译缓存（plugin 侧）回归：盯四件事——
 * ① 首次下发即编译；② 内容版本不变时复用已编译快照（每请求新实例也不重复编译）；
 * ③ 内容版本变化才重编译；④ 空词表 fail-open 空快照，定时刷新用最近词表防御性重建。
 *
 * <p>匹配行为（自动机扫描）由 {@code ModerationGuardTest} 覆盖，本类只验证编译缓存的去重与刷新。</p>
 */
class ModerationWordCacheTest {

    private static ModerationTerm term(String word, int severity, long updatedAtMillis) {
        return new ModerationTerm(word, TextNormalizer.normalize(word), "ABUSE", severity, updatedAtMillis);
    }

    private static ModerationWordCache cache() {
        return new ModerationWordCache(new PluginProperties());
    }

    @Test
    void compilesSnapshotForIncomingWords() {
        ModerationWordCache cache = cache();
        List<ModerationTerm> words = List.of(term("badword", 2, 1_000L));

        ModerationSnapshot snapshot = cache.snapshotFor(words);

        assertTrue(!snapshot.empty(), "非空词表应编译出可用自动机");
        assertEquals(1_000L, snapshot.version());
    }

    @Test
    void reusesCompiledSnapshotWhenContentVersionUnchanged() {
        ModerationWordCache cache = cache();

        // 网关每请求下发新列表实例，内容相同（版本同口径 = max(updatedAtMillis)）
        ModerationSnapshot first = cache.snapshotFor(List.of(term("badword", 2, 1_000L)));
        ModerationSnapshot second = cache.snapshotFor(List.of(term("badword", 2, 1_000L)));

        assertSame(first, second, "内容版本未变时应复用已编译快照");
    }

    @Test
    void recompilesWhenContentVersionChanged() {
        ModerationWordCache cache = cache();
        ModerationSnapshot first = cache.snapshotFor(List.of(term("badword", 2, 1_000L)));

        ModerationSnapshot second = cache.snapshotFor(List.of(term("badword", 2, 2_000L)));

        assertNotSame(first, second, "内容版本变化应重编译");
        assertEquals(2_000L, second.version());
    }

    @Test
    void emptyOrNullWordsYieldEmptySnapshot() {
        ModerationWordCache cache = cache();

        assertTrue(cache.snapshotFor(null).empty(), "null 词表应 fail-open 空快照");
        assertTrue(cache.snapshotFor(List.of()).empty(), "空词表应 fail-open 空快照");
    }

    @Test
    void refreshRebuildsFromLastWords() {
        ModerationWordCache cache = cache();
        cache.snapshotFor(List.of(term("badword", 2, 1_000L)));

        cache.refresh();

        ModerationSnapshot after = cache.snapshotFor(List.of(term("badword", 2, 1_000L)));
        assertTrue(!after.empty(), "定时刷新后最近词表仍应编译可用");
        assertEquals(1_000L, after.version());
    }

    @Test
    void refreshIsNoOpWhenNoWordsReceived() {
        ModerationWordCache cache = cache();

        cache.refresh();

        assertTrue(cache.snapshotFor(List.of()).empty(), "未收到过词表时刷新不应产出快照");
    }

    @Test
    void refreshIntervalComesFromProperties() {
        PluginProperties properties = new PluginProperties();
        properties.getModeration().setRefreshInterval(Duration.ofSeconds(30));
        ModerationWordCache cache = new ModerationWordCache(properties);

        assertEquals(Duration.ofSeconds(30), cache.refreshInterval());
    }
}
