package org.chobit.knot.plugin.moderation;

import org.chobit.knot.gateway.moderation.ModerationTerm;
import org.chobit.knot.gateway.util.TextNormalizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick 多模式匹配自动机：一次线性扫描同时命中全部词条，整体 O(n)。
 *
 * <p>不用逐词 {@code contains}（万级词库 × 数十 KB 正文 = 千万次比较），也不用数据库 LIKE。
 * 词库变更时整体重建：重建成本远低于运行期逐词匹配。</p>
 *
 * <p>入参是中性 {@link ModerationTerm}：本模块不依赖持久化实体，词库由网关经插件上下文下发。</p>
 */
public final class AhoCorasickMatcher {

    /** 词条缺失严重度时的兜底值，与 DDL 默认值 2 一致 */
    private static final int DEFAULT_SEVERITY = 2;

    /**
     * 单次命中：{@code [start, end)} 为归一化文本上的前闭后开区间。
     */
    public record Hit(int start, int end, String categoryCode, int severity) {
    }

    private record Term(int length, String categoryCode, int severity) {
    }

    private final List<Map<Character, Integer>> next = new ArrayList<>();
    private final List<Integer> fail = new ArrayList<>();
    private final List<List<Term>> output = new ArrayList<>();
    private final int maxWordLength;
    private final boolean noWords;

    private AhoCorasickMatcher(List<ModerationTerm> words) {
        next.add(new HashMap<>());
        fail.add(0);
        output.add(new ArrayList<>());
        int max = 0;
        if (words != null) {
            for (ModerationTerm word : words) {
                String key = keyOf(word);
                if (key == null || key.isEmpty()) {
                    continue;
                }
                max = Math.max(max, key.length());
                insert(key, word);
            }
        }
        this.maxWordLength = max;
        this.noWords = max == 0;
        buildFailLinks();
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static AhoCorasickMatcher of(List<ModerationTerm> words) {
        return new AhoCorasickMatcher(words);
    }

    private static String keyOf(ModerationTerm word) {
        String normalized = word.normalizedWord();
        if (normalized == null || normalized.isBlank()) {
            // 兜底：词条未预存归一化键时按原文现算
            normalized = TextNormalizer.normalize(word.word());
        }
        return normalized == null ? null : normalized.trim();
    }

    private static int severityOf(ModerationTerm word) {
        return word.severity() == null ? DEFAULT_SEVERITY : word.severity();
    }

    private void insert(String key, ModerationTerm word) {
        int state = 0;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            Integer child = next.get(state).get(c);
            if (child == null) {
                child = next.size();
                next.get(state).put(c, child);
                next.add(new HashMap<>());
                fail.add(0);
                output.add(new ArrayList<>());
            }
            state = child;
        }
        output.get(state).add(new Term(key.length(), word.categoryCode(), severityOf(word)));
    }

    /**
     * BFS 构造 fail 链并合并后缀输出，使扫描时每个字符只前进一步。
     */
    private void buildFailLinks() {
        Queue<Integer> queue = new ArrayDeque<>();
        for (Integer child : next.get(0).values()) {
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (Map.Entry<Character, Integer> edge : next.get(current).entrySet()) {
                int child = edge.getValue();
                char c = edge.getKey();
                int fallback = fail.get(current);
                while (fallback != 0 && !next.get(fallback).containsKey(c)) {
                    fallback = fail.get(fallback);
                }
                Integer via = next.get(fallback).get(c);
                int target = (via == null || via == child) ? 0 : via;
                fail.set(child, target);
                output.get(child).addAll(output.get(target));
                queue.add(child);
            }
        }
    }

    /**
     * 词库为空：调用方据此短路，避免无意义扫描。
     */
    public boolean empty() {
        return noWords;
    }

    /**
     * 最长词条长度：流式增量扫描据此外扩滑动窗口边界（M2）。
     */
    public int maxWordLength() {
        return maxWordLength;
    }

    /**
     * 扫描已归一化的文本，返回全部命中（含重叠与多分类）。
     */
    public List<Hit> scan(String text) {
        List<Hit> hits = new ArrayList<>();
        if (noWords || text == null || text.isEmpty()) {
            return hits;
        }
        int state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (state != 0 && !next.get(state).containsKey(c)) {
                state = fail.get(state);
            }
            Integer child = next.get(state).get(c);
            state = child == null ? 0 : child;
            List<Term> terms = output.get(state);
            if (terms.isEmpty()) {
                continue;
            }
            int end = i + 1;
            for (Term term : terms) {
                hits.add(new Hit(end - term.length(), end, term.categoryCode(), term.severity()));
            }
        }
        return hits;
    }
}
