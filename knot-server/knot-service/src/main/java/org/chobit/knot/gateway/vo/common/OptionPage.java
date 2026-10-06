package org.chobit.knot.gateway.vo.common;

import java.util.List;
import java.util.function.Function;

/**
 * 下拉候选分页结果。约定见 options-refactor-constraints.md 第 1 节。
 *
 * <p>{@code missingValues} 承载请求 {@code values} 中不存在 / 无权限的项，
 * 前端须统一展示「已选项不存在或无权限」并阻止非法提交，不得静默伪造 {@code #id}。</p>
 */
public record OptionPage<T>(
        List<T> list,
        long total,
        int pageNum,
        int pageSize,
        List<String> missingValues
) {
    public static <T> OptionPage<T> of(
            List<T> list, long total, int pageNum, int pageSize, List<String> missingValues) {
        return new OptionPage<>(
                list, total, pageNum, pageSize, missingValues == null ? List.of() : missingValues);
    }

    public static <T> OptionPage<T> of(List<T> list, long total, int pageNum, int pageSize) {
        return of(list, total, pageNum, pageSize, List.of());
    }

    public <R> OptionPage<R> map(Function<T, R> mapper) {
        List<R> mapped = list.stream().map(mapper).toList();
        return new OptionPage<>(mapped, total, pageNum, pageSize, missingValues);
    }

    public <R> OptionPage<R> mapList(Function<List<T>, List<R>> converter) {
        return new OptionPage<>(converter.apply(list), total, pageNum, pageSize, missingValues);
    }
}
