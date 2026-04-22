import { get, post, put } from "./http";

export function listApps() {
  return get("/api/apps");
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
  return get(`/api/apps/${id}/metrics`);
}
