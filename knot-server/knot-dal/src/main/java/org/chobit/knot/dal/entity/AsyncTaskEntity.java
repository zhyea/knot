package org.chobit.knot.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步任务实例（{@code kb_async_tasks}）：一次「提交 + 轮询」= 一行。
 *
 * <p><b>DB 是唯一真相源</b>：任务不放在 Redis / 进程内存里，缓存不可用不应放大成任务丢失。
 * 三个 JSON 段各司其职，缺一不可：</p>
 * <ul>
 *   <li>{@code requestJson}：客户端原始请求体，终态提取计费属性的<b>唯一</b>输入
 *       （提取器读的就是这一份，不是转换后的上游体 —— 见 {@code UsageExtractorRegistry}）；</li>
 *   <li>{@code pollSpecJson}：提交时快照的查询口径，档案改了也不影响在途任务，保证可复现；</li>
 *   <li>{@code contextJson}：重建计费与事件上下文（路由/命中/追踪）。</li>
 * </ul>
 *
 * <p>三个 JSON 段在实体里以<b>文本</b>承载：MySQL 的 JSON 列接受字符串字面量，
 * 读取也以字符串返回，序列化策略由调用方（{@code JsonKit}）唯一决定 ——
 * 不引入 typeHandler，避免「同一个字段两种序列化口径」。</p>
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
     * 3-FAILED 4-EXPIRED 5-CANCELLED。
     *
     * <p>⚠ 实体故意<b>不</b>声明成枚举类型：dal 不依赖 gateway（枚举在那边），
     * 若在这里建一个同形态的副本枚举，就成了「同一个状态集的两份定义」，必然漂移。
     * 需要枚举语义时由 gateway 侧 {@code AsyncTaskStatusEnum.fromCode} 转换。</p>
     */
    private Integer status;
    private Integer attempts;
    /**
     * 重试轮次（0 = 从未被管理端重试过）。
     *
     * <p>⭐ <b>它存在的唯一理由是「同一条任务可能有多条终态事件」</b>：管理端把
     * {@code FAILED} / {@code EXPIRED} 重新打开后，{@code attempts} 归零、{@code task_query_seq}
     * 从 1 重新计数。若没有轮次字段，重试后的第 1 次查询与上一轮的第 1 次查询在事件流里
     * 完全同键（{@code task_code + task_phase + task_query_seq}），数仓按该键去重会把两次
     * 真实的上游调用压成一条——方向是「少记」，事后无从还原。</p>
     *
     * <p>⚠ 它<b>不参与</b>状态机判定、不影响退避与超时，纯粹是数据侧的轮次坐标。</p>
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
    private String errorCode;
    private String errorMessage;
    private String billingRuleCode;
    private String billingRuleVersionCode;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime resultExpiresAt;

    /**
     * 客户端回推地址；{@code null} 表示本任务不回调。
     *
     * <p>它从客户端原始请求体提取并落库，但<b>不会</b>跟着请求体发给上游
     * （{@code VideoRequestSupport} 负责剥除）——上游直接推终态会让这笔用量绕过结算。</p>
     */
    private String callbackUrl;
    /**
     * 回调交付状态（{@code AsyncTaskDeliveryStatusEnum.code()}）：0-无需回调 1-待回调 2-成功 3-失败。
     *
     * <p>⭐ 与 {@code status} <b>正交</b>：生成成功不代表回调成功。合成一个枚举会出现
     * 「某终态代表回调失败、不代表任务失败」这种自相矛盾的取值。</p>
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
