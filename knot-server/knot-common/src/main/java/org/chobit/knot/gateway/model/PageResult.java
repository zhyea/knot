package org.chobit.knot.gateway.model;

import com.github.pagehelper.PageInfo;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果封装。
 */
public record PageResult<T>(
        List<T> list,
        long total,
        int pageNum,
        int pageSize,
        int pages
) {
    /**
     * Creates a page result.
     */
    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        int pages = (int) ((total + pageSize - 1) / pageSize);
        return new PageResult<>(list, total, pageNum, pageSize, pages);
    }

    /**
     * Builds a page result from PageInfo.
     */
    public static <E, T> PageResult<T> fromPage(PageInfo<E> pageInfo, Function<List<E>, List<T>> converter, PageRequest pageRequest) {
        List<T> list = converter.apply(pageInfo.getList());
        return PageResult.of(list, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    /**
     * Maps each element in the page result.
     */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        List<R> mappedList = list.stream().map(mapper).toList();
        return new PageResult<>(mappedList, total, pageNum, pageSize, pages);
    }

    /**
     * Maps the full list in the page result.
     */
    public <R> PageResult<R> mapList(Function<List<T>, List<R>> converter) {
        return new PageResult<>(converter.apply(list), total, pageNum, pageSize, pages);
    }
}
