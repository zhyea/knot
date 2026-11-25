import {postQuery, post, put, get} from "./http";
import type {Dict} from "@/types";

export function listScheduledTasks(params: Dict) {
  return postQuery("/api/system/scheduled-tasks/list", params);
}

/** 任务详情：编辑抽屉打开时按 id 精确取，不复用列表行数据。 */
export function getScheduledTask(id: number | string) {
  return get<Dict>(`/api/system/scheduled-tasks/${id}`);
}

export function createScheduledTask(data: Dict) {
  return post("/api/system/scheduled-tasks", data);
}

export function updateScheduledTask(id: number | string, data: Dict) {
  return put(`/api/system/scheduled-tasks/${id}`, data);
}

export function triggerScheduledTask(id: number | string) {
  return post(`/api/system/scheduled-tasks/${id}/trigger`);
}

export function listScheduledTaskRuns(params: Dict) {
  return postQuery("/api/system/scheduled-tasks/runs", params);
}

/** 已注册的处理器编码（来源：Spring 容器中的 ScheduledTaskHandler bean），供任务表单下拉使用 */
export function listScheduledTaskHandlers() {
  return post<string[]>("/api/system/scheduled-tasks/handlers");
}
