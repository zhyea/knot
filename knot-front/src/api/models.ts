import { postQuery, post, put, get } from "./http";
import type { Dict } from "../types";

export function listModels(params: Dict) {
  return postQuery("/api/models/list", params);
}

export function getModel(id: number | string) {
  return get(`/api/models/${id}`);
}

export function checkModelCode(code: string, excludeId: number | string) {
  return get("/api/models/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function listUsageExtractors() {
  return get("/api/models/usage-extractors");
}

export function listRequestAdapters() {
  return get("/api/models/request-adapters");
}

export function listModelTypes() {
  return get("/api/models/types");
}

export function createModel(payload: Dict) {
  return post("/api/models", payload);
}

export function updateModel(id: number | string, payload: Dict) {
  return put(`/api/models/${id}`, payload);
}

export function updateModelStatus(id: number | string, enabled: boolean) {
  return put(`/api/models/${id}/status`, { enabled });
}
