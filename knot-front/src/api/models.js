import { postQuery, post, put, get } from "./http";

export function listModels(params) {
  return postQuery("/api/models/list", params);
}

export function getModel(id) {
  return get(`/api/models/${id}`);
}

export function checkModelCode(code, excludeId) {
  return get("/api/models/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function listUsageExtractors() {
  return get("/api/models/usage-extractors");
}

export function createModel(payload) {
  return post("/api/models", payload);
}

export function updateModel(id, payload) {
  return put(`/api/models/${id}`, payload);
}

export function testModel(id, payload) {
  return post(`/api/models/${id}/test`, payload);
}
