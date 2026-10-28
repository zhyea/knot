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
    private String externalTaskId;
    /** 任务状态（AsyncTaskStatusEnum）：PENDING / POLLING / SUCCEEDED / FAILED / EXPIRED / CANCELLED */
    private String status;
    private Integer attempts;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
