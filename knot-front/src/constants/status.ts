/**
 * 数字化业务状态常量（唯一来源：/api/common/enums 下发的代码枚举）。
 *
 * 规则：
 * - 二态启用状态只有 1/0，0 是合法值——禁止 truthy 判断（`if (status)` / `status || 1`），
 *   一律用 `===` 与本文件常量比较。
 * - 多态状态（插件实例/发布状态等）按各自枚举的 code 顺序声明。
 * - 前端不再新建第二份枚举定义；本文件只做字面量收口，label 展示一律走 useEnumOptions。
 */

/** 启用状态（EnabledStatusEnum） */
export const EnabledStatus = {
  ENABLED: 1,
  DISABLED: 0
} as const;

/** 操作日志执行结果（OperationLogStatusEnum） */
export const OperationLogStatus = {
  SUCCESS: 1,
  FAILURE: 2
} as const;

/** 定时任务运行状态（ScheduledTaskRunStatusEnum） */
export const ScheduledTaskRunStatus = {
  RUNNING: 1,
  SUCCESS: 2,
  FAILURE: 3
} as const;

/** 统一模型发布状态（LogicalModelPublishStatusEnum） */
export const LogicalModelPublishStatus = {
  DRAFT: 1,
  PUBLISHED: 2,
  ARCHIVED: 3
} as const;

/** 插件实例状态（PluginInstanceStatusEnum） */
export const PluginInstanceStatus = {
  DRAFT: 1,
  ACTIVE: 2,
  PAUSED: 3,
  ARCHIVED: 4
} as const;

/** 外部模型同步状态（ExternalModelSyncStatusEnum） */
export const ExternalModelSyncStatus = {
  PENDING: 1,
  SYNCED: 2,
  FAILED: 3
} as const;

/** 插件包状态（PluginPackageStatusEnum） */
export const PluginPackageStatus = {
  ACTIVE: 1,
  DISABLED: 2,
  DEPRECATED: 3
} as const;
