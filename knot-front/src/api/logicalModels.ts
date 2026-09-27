import { del, get, post, postQuery, put } from "./http";
import type { Dict } from "../types";

export function listLogicalModels(params: Dict) {
  return postQuery("/api/logical-models/list", params);
}

export function getLogicalModel(id: number | string) {
  return get(`/api/logical-models/${id}`);
}

export function checkLogicalModelCode(code: string, excludeId: number | string | null) {
  return get("/api/logical-models/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createLogicalModel(payload: Dict) {
  return post("/api/logical-models", payload);
}

export function updateLogicalModel(id: number | string, payload: Dict) {
  return put(`/api/logical-models/${id}`, payload);
}

export function updateLogicalModelStatus(id: number | string, enabled: boolean) {
  return put(`/api/logical-models/${id}/status`, { enabled });
}

export function deleteLogicalModel(id: number | string) {
  return del(`/api/logical-models/${id}`);
}
