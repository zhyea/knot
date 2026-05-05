import { postQuery, post, put } from "./http";

export function listModels(params) {
  return postQuery("/api/models/list", params);
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

export function switchModelVersion(id, payload) {
  return post(`/api/models/${id}/versions/switch`, payload);
}
