export function taskModeLabel(value) {
  return value === "BROADCAST" ? "广播" : "单节点";
}

export function taskStatusLabel(value) {
  return value === "ENABLED" ? "启用" : "禁用";
}

export function runStatusLabel(value) {
  const map = { RUNNING: "运行中", SUCCESS: "成功", FAILURE: "失败" };
  return map[value] || value || "-";
}

export function runStatusType(value) {
  if (value === "SUCCESS") return "success";
  if (value === "FAILURE") return "danger";
  return "warning";
}
