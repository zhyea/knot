package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;

import java.util.Map;
import java.util.function.Function;

/**
 * 审计快照统一骨架：{@code @OperationLog} 的 SpEL 在业务方法前后各取一次快照，采集本身不得改变业务请求的结果。
 * 骨架收敛到一处，加载逻辑由调用方以 {@code Function<Long, Map<String, Object>>} 传入。
 * 只吞 {@link BusinessException}（业务可预期分支：实体不存在、重复删除）；其它 RuntimeException 必须冒出去。
 */
public final class AuditSnapshotSupport {

    private AuditSnapshotSupport() {
    }

    /**
     * 按统一骨架采集审计快照。
     *
     * @param id     业务主键；为 {@code null} 时直接返回 {@code null}，加载器不执行
     *               （SpEL 的 {@code #p0} 理论上不会为空，此处是防御而非主要路径）
     * @param loader 加载并序列化快照；返回 {@code null} 表示实体已不存在
     * @return 快照；id 为空、实体不存在、或加载抛业务异常时为 {@code null}
     */
    public static Map<String, Object> capture(Long id, Function<Long, Map<String, Object>> loader) {
        if (id == null) {
            return null;
        }
        try {
            return loader.apply(id);
        } catch (BusinessException e) {
            return null;
        }
    }
}
