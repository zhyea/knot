import { postQuery, post, put } from "./http";

export function listApps(params) {
  return postQuery("/api/apps", params);
}

export function createApp(payload) {
  return post("/api/apps", payload);
}

export function updateApp(id, payload) {
  return put(`/api/apps/${id}`, payload);
}

export function updateAppQuota(id, quotaPolicy) {
  return put(`/api/apps/${id}/quota`, quotaPolicy);
}

export function getAppMetrics(id) {
  return postQuery(`/api/apps/${id}/metrics`);
}
