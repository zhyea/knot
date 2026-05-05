import { postQuery, post, put } from "./http";

export function listPlugins(params) {
  return postQuery("/api/plugins", params);
}

export function createPlugin(payload) {
  return post("/api/plugins", payload);
}

export function updatePluginStatus(id, payload) {
  return put(`/api/plugins/${id}/status`, payload);
}
