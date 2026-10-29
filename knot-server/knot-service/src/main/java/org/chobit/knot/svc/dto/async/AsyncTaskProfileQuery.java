package org.chobit.knot.svc.dto.async;

import org.chobit.knot.common.model.PageRequest;

/**
 * 异步任务档案管理面的检索条件（对应 {@code kb_async_task_profiles} 的列表页）。
 *
 * <p>与 {@link AsyncTaskQuery} 同源同形，理由也一样：它是「这张表怎么被查」的契约，
 * 放 admin 会让 svc 反过来依赖 app 的类型。</p>
 *
 * @param keyword  档案编码 / 备注 的模糊匹配
 * @param protocol API 协议编码；{@code null} 或空表示不限
 * @param status   启用状态码（{@code EnabledStatusEnum.code()}）；{@code null} 表示全部
 */
public record AsyncTaskProfileQuery(Integer pageNum,
                                    Integer pageSize,
                                    String keyword,
                                    String protocol,
                                    Integer status) {

    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
