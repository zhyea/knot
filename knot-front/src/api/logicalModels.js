import { del, get, post, postQuery } from "./http";

export function listLogicalModels(params) {
  return postQuery("/api/logical-models/list", params);
}

export function getLogicalModel(id) {
  return get(`/api/logical-models/${id}`);
}

export function checkLogicalModelCode(code, excludeId) {
  return get("/api/logical-models/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createLogicalModel(payload) {
  return post("/api/logical-models", payload);
}

export function updateLogicalModel(id, payload) {
  return put(`/api/logical-models/${id}`, payload);
}

export function deleteLogicalModel(id) {
  return del(`/api/logical-models/${id}`);
}
