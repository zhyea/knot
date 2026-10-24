package org.chobit.knot.gateway.routing;

import org.chobit.knot.gateway.constants.enums.ModelPoolSelectionStrategyEnum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.springframework.stereotype.Component;

/**
 * 模型池选择策略的运行时实现。
 *
 * <p>模型池配置 {@code selection_strategy} 后，池内候选必须按该策略给出选中顺序：
 * 首个元素即本次选中的模型，其余按顺序作为故障转移候选。此前无论配置什么策略都固定按
 * priority/weight 排序，导致策略形同虚设。</p>
 *
 * <ul>
 *   <li>{@code PRIORITY}：优先级高者优先，同优先级按 id 升序，结果稳定可复现。</li>
 *   <li>{@code RANDOM}：可用候选均匀随机排序。</li>
 *   <li>{@code WEIGHTED}：按权重做无放回加权抽样（Efraimidis-Spirakis），
 *       权重越大越容易被排到前面，且长期命中比例收敛到权重占比。</li>
 *   <li>{@code LEAST_LOAD}：按<b>在途并发</b>（{@link org.chobit.knot.gateway.load.ModelLoadStore}）
 *       升序，最空闲者优先；在途相同按权重降序、再按 id 升序。确定性排序，不含随机。</li>
 * </ul>
 *
 * <p>随机源可注入，便于用固定 seed 做可重复测试。</p>
 */
@Component
public final class ModelPoolSelection {

    private final Random random;

    public ModelPoolSelection() {
        this(new Random());
    }

    public ModelPoolSelection(Random random) {
        this.random = random;
    }

    /**
     * 按策略给出池内候选的选中顺序。
     *
     * @param candidates  可用候选，调用方需保证模型本身可用
     * @param strategyCode 模型池的 selection_strategy；非法或空白直接抛错，
     *                     不做静默回退（写入入口已强校验，库中值保证合法）
     * @param priorityOf  候选优先级
     * @param weightOf    候选权重
     * @param tieBreakerOf 稳定兜底排序键（通常为候选主键）
     * @param loadOf      候选在途并发（仅 {@code LEAST_LOAD} 使用；取不到按 0 处理）
     */
    public <T> List<T> order(List<T> candidates,
                             String strategyCode,
                             ToIntFunction<T> priorityOf,
                             ToIntFunction<T> weightOf,
                             ToLongFunction<T> tieBreakerOf,
                             ToLongFunction<T> loadOf) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            return List.copyOf(candidates);
        }
        ModelPoolSelectionStrategyEnum strategy =
                ModelPoolSelectionStrategyEnum.require(strategyCode, "unsupported selection strategy: " + strategyCode);
        return switch (strategy) {
            case PRIORITY -> orderByPriority(candidates, priorityOf, tieBreakerOf);
            case RANDOM -> shuffled(candidates);
            case WEIGHTED -> weightedOrder(candidates, weightOf, tieBreakerOf);
            case LEAST_LOAD -> orderByLoad(candidates, loadOf, weightOf, tieBreakerOf);
        };
    }

    /**
     * 最空闲优先：在途并发升序 → 权重降序 → 稳定兜底键升序。
     *
     * <p>在途全为 {@code 0}（冷启动 / 计量不可用）时退化为「权重降序 + 兜底键升序」，
     * 即 WEIGHTED 的确定性版本——不含随机，行为可预期。</p>
     */
    private <T> List<T> orderByLoad(List<T> candidates,
                                    ToLongFunction<T> loadOf,
                                    ToIntFunction<T> weightOf,
                                    ToLongFunction<T> tieBreakerOf) {
        Comparator<T> byWeightDesc = Comparator.comparingInt((T item) -> weightOf.applyAsInt(item)).reversed();
        List<T> copy = new ArrayList<>(candidates);
        copy.sort(Comparator.comparingLong((T item) -> loadOf.applyAsLong(item))
                .thenComparing(byWeightDesc)
                .thenComparingLong(tieBreakerOf::applyAsLong));
        return List.copyOf(copy);
    }

    private <T> List<T> orderByPriority(List<T> candidates,
                                        ToIntFunction<T> priorityOf,
                                        ToLongFunction<T> tieBreakerOf) {
        List<T> copy = new ArrayList<>(candidates);
        copy.sort(Comparator.comparingInt((T item) -> priorityOf.applyAsInt(item)).reversed()
                .thenComparingLong(tieBreakerOf::applyAsLong));
        return List.copyOf(copy);
    }

    private <T> List<T> shuffled(List<T> candidates) {
        List<T> copy = new ArrayList<>(candidates);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            java.util.Collections.swap(copy, i, j);
        }
        return List.copyOf(copy);
    }

    /**
     * 无放回加权抽样：key = u^(1/w)，按 key 降序。w <= 0 视为 0，排在最末。
     */
    private <T> List<T> weightedOrder(List<T> candidates,
                                      ToIntFunction<T> weightOf,
                                      ToLongFunction<T> tieBreakerOf) {
        List<Keyed<T>> keyed = new ArrayList<>(candidates.size());
        for (T item : candidates) {
            keyed.add(new Keyed<>(item, weightedKey(weightOf.applyAsInt(item))));
        }
        keyed.sort(Comparator.comparingDouble(Keyed<T>::key).reversed()
                .thenComparingLong(item -> tieBreakerOf.applyAsLong(item.item())));
        List<T> result = new ArrayList<>(keyed.size());
        for (Keyed<T> entry : keyed) {
            result.add(entry.item());
        }
        return List.copyOf(result);
    }

    private double weightedKey(int weight) {
        if (weight <= 0) {
            return 0.0;
        }
        double u = random.nextDouble();
        if (u <= 0.0) {
            u = Double.MIN_VALUE;
        }
        return Math.pow(u, 1.0 / weight);
    }

    private record Keyed<T>(T item, double key) {
    }
}
