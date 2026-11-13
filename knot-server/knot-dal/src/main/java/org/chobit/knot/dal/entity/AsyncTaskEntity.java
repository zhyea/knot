package org.chobit.knot.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步任务实例（{@code kb_async_tasks}）：一次「提交 + 轮询」= 一行。
 * 三个 JSON 段：{@code requestJson} 客户端原始请求体（终态提取计费属性的唯一输入）；
 * {@code pollSpecJson} 提交时快照的查询口径；{@code contextJson} 重建计费与事件上下文。
 * 三段在实体里以文本承载，序列化策略由调用方 {@code JsonKit} 唯一决定。
 */
@Data
public class AsyncTaskEntity {

    private Long id;
    /** 对外任务句柄（客户端轮询用，非主键 id） */
    private String taskCode;
    private String profileCode;
    private String protocol;
    private String upstreamTaskId;
    /**
     * 任务状态（{@code AsyncTaskStatusEnum.code()}）：0-PENDING 1-POLLING 2-SUCCEEDED
     * 3-FAILED 4-EXPIRED 5-CANCELLED。字段为整型，需要枚举语义时由 gateway 侧
     * {@code AsyncTaskStatusEnum.fromCode} 转换。
     */
    private Integer status;
    private Integer attempts;
    /**
     * 重试轮次（0 = 从未被管理端重试过）。管理端重开任务时 +1，它是终态事件流的去重坐标
     * （{@code task_code + task_phase + task_query_seq + 轮次}）；不参与状态机判定，
     * 不影响退避与超时。
     */
    private Integer retryRound;
    /** 下次轮询时刻；终态为 null，天然退出扫描集 */
    private LocalDateTime nextPollAt;
    /** 轮询占位标识（nodeId-uuid），防多节点重复处理 */
    private String claimId;
    /** 占位过期时刻；节点宕机后自动可回收，无需显式释放 */
    private LocalDateTime claimExpiresAt;
    private LocalDateTime lastPolledAt;
    private String requestJson;
    private String pollSpecJson;
    private String contextJson;
    private String resultJson;
    /**
     * 上游终态查询响应原文（原样，不解析、不裁剪），供计费复现与排障使用。
     * 与 {@code resultJson} 分开：后者是对外契约，客户端轮询/回调原样读 {@code result}，
     * 上游原文不混入其中。
     */
    private String terminalBody;
    /**
     * 终态结算快照，与同步链路 {@code knot_extend} 同形（{@code {usage, billing}}）。
     * 失败/超时/取消不计费 → 恒为 {@code null}（不写零账）。
     */
    private String settlementJson;
    private String errorCode;
    private String errorMessage;
    private String billingRuleCode;
    private String billingRuleVersionCode;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime resultExpiresAt;

    /**
     * 客户端回推地址；{@code null} 表示本任务不回调。由客户端原始请求体提取并落库，
     * 不随请求体发给上游（由 {@code VideoRequestSupport} 剥除）。
     */
    private String callbackUrl;
    /**
     * 回调交付状态（{@code AsyncTaskDeliveryStatusEnum.code()}）：0-无需回调 1-待回调
     * 2-成功 3-失败。与 {@code status} 正交：生成成功不代表回调成功。
     */
    private Integer deliveryStatus;
    /** 已尝试投递次数（含失败重试） */
    private Integer deliveryAttempts;
    /** 下一次投递时刻；{@code null} 表示不在待投递集 */
    private LocalDateTime nextDeliveryAt;
    /** 成功送达时刻 */
    private LocalDateTime deliveredAt;
    /** 最近一次投递失败原因摘要 */
    private String deliveryError;
    /** 交付维度占位标识（与轮询的 {@code claimId} 分开：交付发生在终态之后，不能复用轮询租约） */
    private String deliveryClaimId;
    /** 交付占位过期时刻；节点宕机后自动可回收 */
    private LocalDateTime deliveryClaimExpiresAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
