export function taskModeLabel(value: unknown): string {
  return value === "BROADCAST" ? "广播" : "单节点";
}

export function taskStatusLabel(value: unknown): string {
  return value === "ENABLED" ? "启用" : "禁用";
}

export function runStatusLabel(value: unknown): string {
  const map: Record<string, string> = { RUNNING: "运行中", SUCCESS: "成功", FAILURE: "失败" };
  return map[String(value)] || String(value || "-");
}

export function runStatusType(value: unknown): string {
  if (value === "SUCCESS") return "success";
  if (value === "FAILURE") return "danger";
  return "warning";
}
