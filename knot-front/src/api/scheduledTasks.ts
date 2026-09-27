import {postQuery, post, put} from "./http";
import type {Dict} from "@/types";

export function listScheduledTasks(params: Dict) {
  return postQuery("/api/system/scheduled-tasks/list", params);
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
