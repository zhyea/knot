package org.chobit.knot.svc.dto.async;

import org.chobit.knot.common.model.PageRequest;

import java.time.LocalDateTime;

/**
 * 异步任务管理面的检索条件（对应 {@code kb_async_tasks} 的列表页）。
 *
 * 时间字段是 {@link LocalDateTime}，前端 {@code el-date-picker} 以
 * {@code value-format="YYYY-MM-DDTHH:mm:ss"} 送出 ISO 文本，由 Jackson 直接反序列化。
 *
 * @param keyword       任务句柄 / 上游任务号 的模糊匹配
 * @param profileCode   异步任务档案编码
 * @param protocol      API 协议编码
 * @param status        状态码（{@code AsyncTaskStatusEnum.code()}）；{@code null} 表示全部
 * @param submittedFrom 提交时间下界（含）
 * @param submittedTo   提交时间上界（含）
 */
public record AsyncTaskQuery(
        Integer pageNum,
        Integer pageSize,
        String keyword,
        String profileCode,
        String protocol,
        Integer status,
        LocalDateTime submittedFrom,
        LocalDateTime submittedTo
) {

    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
