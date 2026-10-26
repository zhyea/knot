/**
 * 数字化业务状态常量（唯一来源：/api/enum-options 下发的代码枚举）。
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

/** 路由目标类型（RouteTargetTypeEnum）：落库 code；label 走 enumLabelOf("RouteTargetTypeEnum", …) */
export const RouteTargetType = {
  MODEL: "MODEL",
  MODEL_POOL: "MODEL_POOL"
} as const;

/** 计费模式（BillingModeEnum）：落库 code = 枚举常量名 */
export const BillingMode = {
  TOKEN: "TOKEN",
  REQUEST: "REQUEST",
  IMAGE: "IMAGE",
  AUDIO: "AUDIO",
  VIDEO: "VIDEO",
  EMBEDDING: "EMBEDDING",
  FREE: "FREE",
  CUSTOM: "CUSTOM"
} as const;

/** 计价方案（PricingPlanEnum）：⚠ code() 返回 name()，第一个构造参数是 label（"固定价"），不要拿 label 当 code */
export const PricingPlan = {
  FIXED: "FIXED",
  TIERED: "TIERED",
  PEAK_OFF_PEAK: "PEAK_OFF_PEAK",
  VENDOR_MINIMAX: "VENDOR_MINIMAX",
  VENDOR_KLING: "VENDOR_KLING"
} as const;

/** 计费单位（BillingUnitEnum）：⚠ code 是 "1K_TOKENS" 这类，常量名是 ONE_K_TOKENS，两者不同 */
export const BillingUnit = {
  ONE_K_TOKENS: "1K_TOKENS",
  ONE_M_TOKENS: "1M_TOKENS",
  PER_TOKEN: "PER_TOKEN",
  PER_REQUEST: "PER_REQUEST",
  PER_IMAGE: "PER_IMAGE",
  PER_SECOND: "PER_SECOND",
  CUSTOM: "CUSTOM"
} as const;

/** 路由测试结论（RoutingTestStatusEnum）：后端只产这两个值 */
export const RoutingTestStatus = {
  SUCCESS: "SUCCESS",
  FAILED: "FAILED"
} as const;

/**
 * 路由测试**运行态**：抽屉本地状态机的中间态，⚠ **后端枚举不含这三个值**。
 *
 * 与 {@link RoutingTestStatus} 的区分：SUCCESS/FAILED 是网关回了结论（有 httpStatus/响应体），
 * 本组是「结论尚未形成」——RUNNING 在途、STOPPED 用户主动停止、ERROR 压根没拿到结论
 * （网络失败 / 解析失败）。消费方按 {@link RoutingTestStatus} 判定是否为后端结论，
 * 本组一律按「无结论」处理。
 */
export const RoutingTestRunState = {
  RUNNING: "RUNNING",
  STOPPED: "STOPPED",
  ERROR: "ERROR"
} as const;
