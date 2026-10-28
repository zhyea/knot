package org.chobit.knot.dal.entity;

import lombok.Data;

/**
 * 异步任务查询口径档案（{@code kb_async_task_profiles}）：描述「怎么查上游」，一家异步上游一行。
 *
 * <p>与任务实例表分离是通用性的根：查询路径、终态判定、产物位置、产物时效逐家不同，
 * 塞进任务行会无法复用。新增一家异步上游 = 加一行档案 + 绑定引用，不改表、不改代码。</p>
 *
 * <p>路径模板里的 {@code {task_id}} 占位可出现在 path 段或 query 段
 * （可灵是 {@code /tasks?task_ids={task_id}}，MiniMax 是 {@code /v2/query/video_generation/{task_id}}）。</p>
 *
 * <p>⚠ 2026-10-28 起：三个 {@code *Path} 是 <b>MVEL 取值表达式</b>，三个 {@code status*} 是
 * <b>MVEL 布尔表达式</b>（变量 {@code status} / {@code body}）。求值口径见
 * {@code AsyncTaskExpressionSupport}；判定语义见规范 {@code 60-business-rules.md} §8.14。</p>
 */
@Data
public class AsyncTaskProfileEntity {

    private Long id;
    /** 档案编码（绑定引用，非主键 id） */
    private String profileCode;
    /** 所属 API 协议编码（ModelApiProtocolEnum），用于绑定一致性校验 */
    private String protocol;
    private String queryMethod;
    private String queryPath;
    /** 创建响应取上游任务 ID 的 MVEL 表达式，如 {@code data.id} */
    private String taskIdPath;
    /** 查询响应取状态文本的 MVEL 表达式，如 {@code data[0].status} */
    private String statusPath;
    /** 成功判定 MVEL 布尔表达式，如 {@code ['succeed','SUCCEEDED'] contains status} */
    private String statusSucceeded;
    /** 失败判定 MVEL 布尔表达式，如 {@code status == 'failed'} */
    private String statusFailed;
    /** 进行中判定 MVEL 布尔表达式；为空表示「除成功/失败外一律视为非终态」 */
    private String statusRunning;
    /** 终态产物 URL 的 MVEL 表达式，如 {@code data[0].outputs[0].url} */
    private String resultPath;
    /** 上游产物有效期（天） */
    private Integer resultExpiresDays;
    private Integer pollIntervalMs;
    private Integer pollBackoffBaseMs;
    private Integer pollBackoffMaxMs;
    private Integer maxAttempts;
    private Long timeoutMs;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String remark;
}
