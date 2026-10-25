package org.chobit.knot.plugin.moderation;

import org.chobit.knot.common.moderation.ModerationTerm;
import org.chobit.knot.common.util.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhoCorasickMatcherTest {

    private static ModerationTerm term(String word, String categoryCode, int severity) {
        return new ModerationTerm(word, TextNormalizer.normalize(word), categoryCode, severity, null);
    }

    @Test
    void emptyMatcherScansNothing() {
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(List.of());

        assertTrue(matcher.empty());
        assertTrue(matcher.scan("anything").isEmpty());
    }

    @Test
    void findsSingleWord() {
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(List.of(term("badword", "ABUSE", 2)));

        List<AhoCorasickMatcher.Hit> hits = matcher.scan("say badword now");

        assertEquals(1, hits.size());
        assertEquals("badword", "say badword now".substring(hits.get(0).start(), hits.get(0).end()));
        assertEquals("ABUSE", hits.get(0).categoryCode());
        assertEquals(2, hits.get(0).severity());
    }

    @Test
    void findsOverlappingWords() {
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(List.of(term("he", "A", 1), term("she", "B", 2)));

        List<AhoCorasickMatcher.Hit> hits = matcher.scan("she");

        assertEquals(2, hits.size());
        assertTrue(hits.stream().anyMatch(hit -> "A".equals(hit.categoryCode())));
        assertTrue(hits.stream().anyMatch(hit -> "B".equals(hit.categoryCode())));
    }

    @Test
    void findsMultipleOccurrences() {
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(List.of(term("bad", "ABUSE", 3)));

        assertEquals(2, matcher.scan("bad and bad").size());
    }

    @Test
    void usesNormalizedWordAsKey() {
        // 词条预存归一化键时按它建自动机；扫描侧同样在归一化副本上判定
        ModerationTerm term = new ModerationTerm("BadWord", "badword", "ABUSE", 2, null);
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(List.of(term));

        assertEquals(1, matcher.scan(TextNormalizer.normalize("say BADWORD now")).size());
    }

    @Test
    void fallsBackToRawWordWhenNormalizedMissing() {
        ModerationTerm term = new ModerationTerm("badword", null, "ABUSE", 2, null);
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(List.of(term));

        assertEquals(1, matcher.scan("a badword b").size());
    }

    @Test
    void reportsMaxWordLength() {
        AhoCorasickMatcher matcher = AhoCorasickMatcher.of(List.of(term("ab", "A", 1), term("abcd", "A", 1)));

        assertEquals(4, matcher.maxWordLength());
    }
}
