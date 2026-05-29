import { postQuery, post, put } from "./http";

export function listScheduledTasks(params) {
  return postQuery("/api/system/scheduled-tasks/list", params);
}

export function createScheduledTask(data) {
  return post("/api/system/scheduled-tasks", data);
}

export function updateScheduledTask(id, data) {
  return put(`/api/system/scheduled-tasks/${id}`, data);
}

export function triggerScheduledTask(id) {
  return post(`/api/system/scheduled-tasks/${id}/trigger`);
}

export function listScheduledTaskRuns(params) {
  return postQuery("/api/system/scheduled-tasks/runs", params);
}
