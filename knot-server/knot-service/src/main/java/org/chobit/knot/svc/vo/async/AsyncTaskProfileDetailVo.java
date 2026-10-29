package org.chobit.knot.svc.vo.async;

import java.time.LocalDateTime;

/**
 * 异步任务档案的管理面视图：在运行时载体 {@code AsyncTaskProfileDto} 之外，补上
 * 「管理面才关心」的四类信息。
 *
 * <p>⭐ <b>为什么运行时载体不直接加这些字段</b>：{@code AsyncTaskProfileDto} 是网关轮询时的输入，
 * 它刻意不含启用状态与管理字段（见其类注释）。管理面要判断「这条档案现在能不能用、
 * 被谁用着、什么时候改过」，加的正是那三样。两者分居不是重复，而是<b>两种视角</b>：
 * 一个是「怎么查上游」，一个是「这条配置的生命周期」。</p>
 *
 * @param bindingCount 当前引用该档案的模型绑定行数；删除前的占用信号（0 才可删）
 * @param createdAt    创建时刻
 * @param updatedAt    最后修改时刻：改档案不影响在途任务（口径在提交时快照），
 *                     但页面必须让人看见「这条配置什么时候被人动过」
 */
public record AsyncTaskProfileDetailVo(Long id,
                                       String profileCode,
                                       String protocol,
                                       String queryMethod,
                                       String queryPath,
                                       String taskIdPath,
                                       String statusPath,
                                       String statusSucceeded,
                                       String statusFailed,
                                       String statusRunning,
                                       String resultPath,
                                       String resultUrlField,
                                       String errorMessagePath,
                                       Integer resultExpiresDays,
                                       Integer pollIntervalMs,
                                       Integer pollBackoffBaseMs,
                                       Integer pollBackoffMaxMs,
                                       Integer maxAttempts,
                                       Long timeoutMs,
                                       Integer status,
                                       String remark,
                                       Integer bindingCount,
                                       LocalDateTime createdAt,
                                       LocalDateTime updatedAt) {
}
