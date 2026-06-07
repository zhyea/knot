import { del, get, post, postQuery, put } from "./http";

export function listModelPools(params) {
  return postQuery("/api/model-pools/list", params);
}

export function getModelPool(id) {
  return get(`/api/model-pools/${id}`);
}

export function checkModelPoolCode(code, excludeId) {
  return get("/api/model-pools/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createModelPool(payload) {
  return post("/api/model-pools", payload);
}

export function updateModelPool(id, payload) {
  return put(`/api/model-pools/${id}`, payload);
}

export function updateModelPoolStatus(id, enabled) {
  return put(`/api/model-pools/${id}/status`, { enabled });
}

export function deleteModelPool(id) {
  return del(`/api/model-pools/${id}`);
}
