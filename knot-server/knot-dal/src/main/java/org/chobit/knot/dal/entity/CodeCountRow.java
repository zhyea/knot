package org.chobit.knot.dal.entity;

import lombok.Data;

/**
 * 按业务码分组的计数投影行：列 {@code code}（业务码）+ {@code total}（该码的引用行数）。
 *
 * <p>供列表批量化使用：一次 {@code group by} 取回整页的引用计数，替代「每行一次 count」。
 * 未出现在结果里的码即 0，调用侧按 0 兜底。</p>
 */
@Data
public class CodeCountRow {

    private String code;

    private Long total;
}
