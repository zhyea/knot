import { postQuery, post, put, del } from "./http";
import type { Dict } from "../types";

export function listApps(params: Dict) {
  return postQuery("/api/apps/list", params);
}

export function createApp(payload: Dict) {
  return post("/api/apps", payload);
}

export function updateApp(id: number | string, payload: Dict) {
  return put(`/api/apps/${id}`, payload);
}

export function deleteApp(id: number | string) {
  return del(`/api/apps/${id}`);
}

