import {postQuery, put, del} from "./http";
import type {Dict} from "@/types";

export function listPlugins(params: Dict) {
  return postQuery("/api/plugins/list", params);
}

export function updatePluginStatus(id: number | string, payload: Dict) {
  return put(`/api/plugins/${id}/status`, payload);
}

export function deletePlugin(id: number | string) {
  return del(`/api/plugins/${id}`);
}
