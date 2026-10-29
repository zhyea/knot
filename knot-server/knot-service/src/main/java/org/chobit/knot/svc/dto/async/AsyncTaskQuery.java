package org.chobit.knot.svc.dto.async;

import org.chobit.knot.common.model.PageRequest;

import java.time.LocalDateTime;

/**
 * 异步任务管理面的检索条件（对应 {@code kb_async_tasks} 的列表页）。
 *
 * <p>⭐ 放在 {@code svc.dto.async} 而不是 admin 自己的包里：它是「这张表怎么被查」的契约，
 * 与 {@link AsyncTaskDto} 同源；放 admin 会让 svc 的服务方法为了接收条件而反向依赖 admin 的类型。</p>
 *
 * <p>⚠ <b>时间字段是 {@link LocalDateTime}，不是字符串</b>：前端 {@code el-date-picker} 以
 * {@code value-format="YYYY-MM-DDTHH:mm:ss"} 送出 ISO 文本，Spring 的默认 Jackson 配置
 * 能直接反序列化成 {@code LocalDateTime}。若在这里收字符串再自己 parse，就得自己承担
 * 格式错误的分支——而这个接口只在人手点筛选时被调用，省下的那点校验一分不值。</p>
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
