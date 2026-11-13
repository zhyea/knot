package org.chobit.knot.svc.vo.async;

import java.time.LocalDateTime;

/**
 * 异步任务档案的管理面视图：在运行时载体 {@code AsyncTaskProfileDto} 之外，补上「管理面才关心」的四类信息。
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
                                       Long timeoutMinutes,
                                       Integer status,
                                       String remark,
                                       Integer bindingCount,
                                       LocalDateTime createdAt,
                                       LocalDateTime updatedAt,
                                       /** 查询响应取证：从查询响应取上游 usage 对象的 MVEL 表达式；空 = 沿用顶层 usage */
                                       String usagePath) {
}
