package org.chobit.knot.svc.dto.model;

import java.util.List;

/**
 * 外部模型同步/批量导入的结果摘要。
 *
 * <p>{@code total} 是本次处理的条目数，{@code inserted + updated + skipped + failed} 与之相等；
 * 同步路径的 {@code inserted} 指新增条目，导入路径的 {@code inserted} 指新建的统一模型。</p>
 */
public record ExternalModelSyncResult(
        int total,
        int inserted,
        int updated,
        int skipped,
        int failed,
        String message,
        /**
         * 失败明细，最多 {@link #MAX_FAILURES} 条；超过上限只计数并置 {@code failuresTruncated}。
         * 只记「哪一条、什么原因」，不带堆栈。
         */
        List<ItemFailure> failures,
        boolean failuresTruncated
) {

    /** 失败明细上限：够定位问题，又不至于把响应体撑爆。 */
    public static final int MAX_FAILURES = 50;

    public int affectedRows() {
        return inserted + updated;
    }

    /**
     * 单条失败：{@code itemId} 在同步新增时可能为 null（尚未落库），此时靠 {@code modelId} 定位。
     */
    public record ItemFailure(String itemId, String modelId, String reason) {
    }
}
