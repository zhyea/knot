package org.chobit.knot.gateway.routing;

import org.chobit.knot.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelPoolSelectionTest {

    private record Item(String code, int priority, int weight, long id, long load) {
    }

    private final ModelPoolSelection selection = new ModelPoolSelection(new Random(20261012L));

    private static List<Item> order(ModelPoolSelection selection, String strategy, List<Item> items) {
        return selection.order(items, strategy,
                Item::priority, Item::weight, Item::id, Item::load);
    }

    private static List<String> codes(List<Item> items) {
        return items.stream().map(Item::code).toList();
    }

    @Test
    void leastLoadPutsIdlestFirst() {
        List<Item> items = List.of(
                new Item("busy", 100, 100, 1L, 7L),
                new Item("idle", 100, 100, 2L, 0L),
                new Item("mid", 100, 100, 3L, 3L)
        );

        assertEquals(List.of("idle", "mid", "busy"), codes(order(selection, "LEAST_LOAD", items)));
    }

    @Test
    void leastLoadBreaksTiesByWeightThenId() {
        List<Item> items = List.of(
                new Item("same-load-light", 100, 10, 1L, 2L),
                new Item("same-load-heavy", 100, 90, 2L, 2L),
                new Item("same-load-and-weight-later-id", 100, 90, 9L, 2L)
        );

        // 在途相同 → 权重降序；权重也相同 → 主键升序（稳定可复现，不掺随机）
        assertEquals(List.of("same-load-heavy", "same-load-and-weight-later-id", "same-load-light"),
                codes(order(selection, "LEAST_LOAD", items)));
    }

    @Test
    void leastLoadWithNoInflightFallsBackToWeightOrder() {
        List<Item> items = List.of(
                new Item("w10", 100, 10, 1L, 0L),
                new Item("w90", 100, 90, 2L, 0L),
                new Item("w50", 100, 50, 3L, 0L)
        );

        // 冷启动 / 计量不可用（全 0）：退化为「权重降序 + 主键升序」，不含随机
        List<String> first = codes(order(selection, "LEAST_LOAD", items));
        assertEquals(List.of("w90", "w50", "w10"), first);
        // 确定性：多次调用结果一致
        assertEquals(first, codes(order(selection, "LEAST_LOAD", items)));
    }

    @Test
    void leastLoadIsDeterministicAcrossRepeatedCalls() {
        List<Item> items = List.of(
                new Item("a", 100, 100, 1L, 1L),
                new Item("b", 100, 100, 2L, 1L),
                new Item("c", 100, 100, 3L, 1L)
        );

        String first = String.join(",", codes(order(selection, "LEAST_LOAD", items)));
        for (int i = 0; i < 20; i++) {
            assertEquals(first, String.join(",", codes(order(selection, "LEAST_LOAD", items))));
        }
    }

    @Test
    void singleCandidateReturnsAsIs() {
        List<Item> items = List.of(new Item("only", 100, 100, 1L, 5L));
        assertEquals(List.of("only"), codes(order(selection, "LEAST_LOAD", items)));
    }

    @Test
    void priorityStrategyOrdersByPriorityDesc() {
        List<Item> items = List.of(
                new Item("low", 10, 100, 1L, 0L),
                new Item("high", 90, 100, 2L, 9L)
        );

        // PRIORITY 不读在途：高优先级即便更忙也排在前面
        assertEquals(List.of("high", "low"), codes(order(selection, "PRIORITY", items)));
    }

    @Test
    void unknownStrategyRejected() {
        List<Item> items = List.of(
                new Item("a", 100, 100, 1L, 0L),
                new Item("b", 100, 100, 2L, 0L)
        );

        // 非法策略直接抛错，不静默回退——写入入口已强校验，运行期不应出现
        assertThrows(BusinessException.class, () -> order(selection, "NOT_A_STRATEGY", items));
        assertThrows(BusinessException.class, () -> order(selection, null, items));
        assertThrows(BusinessException.class, () -> order(selection, "  ", items));
    }
}
