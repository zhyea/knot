package org.chobit.knot.gateway.model;

import com.github.pagehelper.PageInfo;

import java.util.List;
import java.util.function.Function;

/**
 * 鍒嗛〉缁撴灉灏佽
 */
public record PageResult<T>(
        List<T> list,
        long total,
        int pageNum,
        int pageSize,
        int pages
) {
    /**
     * Executes the public operation. Executes the public operation.
     */
    public static <T> PageResult<T> of(List<T> list, long total, int pageNum, int pageSize) {
        int pages = (int) ((total + pageSize - 1) / pageSize);
        return new PageResult<>(list, total, pageNum, pageSize, pages);
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    /**
     * 浠?PageInfo + 杞崲鍑芥暟鏋勯€犲垎椤电粨鏋滐紝娑堥櫎 Service 灞傛ā鏉夸唬鐮?
     */
    public static <E, T> PageResult<T> fromPage(PageInfo<E> pageInfo, Function<List<E>, List<T>> converter, PageRequest pageRequest) {
        List<T> list = converter.apply(pageInfo.getList());
        return PageResult.of(list, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /**
     * 瀵瑰垎椤电粨鏋滅殑鍏冪礌杩涜绫诲瀷杞崲锛屾秷闄?Controller 灞傛ā鏉夸唬鐮?
     */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        List<R> mappedList = list.stream().map(mapper).toList();
        return new PageResult<>(mappedList, total, pageNum, pageSize, pages);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    /**
     * 瀵瑰垎椤电粨鏋滅殑鍒楄〃鏁翠綋杞崲
     */
    public <R> PageResult<R> mapList(Function<List<T>, List<R>> converter) {
        return new PageResult<>(converter.apply(list), total, pageNum, pageSize, pages);
    }
}
