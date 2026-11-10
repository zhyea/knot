/**
 * 启用态的两个唯一出口（2026-11-10 第五批）。
 *
 * <p>⚠ 这两条只覆盖「列表行 / el-switch 入参」，**不覆盖**另外两类口径：</p>
 * 1. 配置 JSON 的 `Boolean enabled`（retryPolicy 默认开 `!== false`、ejectionPolicy /
 *    stickyPolicy 默认关 `=== true`）——差异是业务默认值，统一会静默改策略行为；
 * 2. 数字状态列 `status`（0 停用 / 1 启用，如 TestRequestPreset）——0 是合法值，
 *    走 `EnabledStatus` 常量比较，不能和 primitive boolean 混在一个函数里。
 *
 * <p>表单回填里 `=== true` / `!!row.enabled` / `!== false` 也各随实体默认值，见报告 §11。</p>
 */

/** el-switch 的 change 入参可能是 boolean / number / string，归一为 primitive boolean */
export function normalizeEnabledFlag(value: boolean | string | number): boolean {
  return value !== false && value !== 0 && value !== "false";
}

/**
 * 列表行启用态：后端 `EnabledStatusEnum.isEnabled(status)` 派生出的 primitive boolean。
 *
 * <p>刻意不用 `=== true`：部分列表行的 enabled 缺省（undefined / null）按「启用」处理；
 * 也刻意不用 truthy：那是把数值状态列混进来的典型错误。</p>
 */
export function isRowEnabled(row: { enabled?: unknown } | null | undefined): boolean {
  return row?.enabled !== false;
}
