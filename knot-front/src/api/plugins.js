import { get, post, put } from "./http";

export function listPlugins() {
  return get("/api/plugins");
}

export function createPlugin(payload) {
  return post("/api/plugins", payload);
}

export function updatePluginStatus(id, payload) {
  return put(`/api/plugins/${id}/status`, payload);
}
