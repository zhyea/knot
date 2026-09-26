import { postQuery, post, put } from "./http";
import type { Dict } from "../types";

export function listPlugins(params: Dict) {
  return postQuery("/api/plugins/list", params);
}

export function createPlugin(payload: Dict) {
  return post("/api/plugins", payload);
}

export function updatePluginStatus(id: number | string, payload: Dict) {
  return put(`/api/plugins/${id}/status`, payload);
}
