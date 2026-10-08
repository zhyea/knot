import { EnabledStatus, ScheduledTaskRunStatus } from "@/constants/status";

export function taskModeLabel(value: unknown): string {
  return value === "BROADCAST" ? "广播" : "单节点";
}

export function taskStatusLabel(value: unknown): string {
  return value === EnabledStatus.ENABLED ? "启用" : "禁用";
}

export function runStatusLabel(value: unknown): string {
  const map: Record<number, string> = {
    [ScheduledTaskRunStatus.RUNNING]: "运行中",
    [ScheduledTaskRunStatus.SUCCESS]: "成功",
    [ScheduledTaskRunStatus.FAILURE]: "失败"
  };
  return map[Number(value)] || String(value ?? "-");
}

export function runStatusType(value: unknown): "primary" | "success" | "warning" | "info" | "danger" {
  if (value === ScheduledTaskRunStatus.SUCCESS) return "success";
  if (value === ScheduledTaskRunStatus.FAILURE) return "danger";
  return "warning";
}
