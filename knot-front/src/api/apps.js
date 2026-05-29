import { postQuery, post, put, del } from "./http";

export function listApps(params) {
  return postQuery("/api/apps/list", params);
}

export function createApp(payload) {
  return post("/api/apps", payload);
}

export function updateApp(id, payload) {
  return put(`/api/apps/${id}`, payload);
}

export function deleteApp(id) {
  return del(`/api/apps/${id}`);
}

