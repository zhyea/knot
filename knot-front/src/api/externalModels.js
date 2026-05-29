import { del, get, post, postQuery } from "./http";

export function listExternalModelSources() {
  return get("/api/external-models/sources");
}

export function listExternalModelItems(params) {
  return postQuery("/api/external-models/items/list", params);
}

export function getExternalModelItem(id) {
  return get(`/api/external-models/items/${id}`);
}

export function syncExternalModelSource(sourceCode) {
  return post(`/api/external-models/sources/${sourceCode}/sync`, {});
}

export function createLogicalModelFromExternalItem(id) {
  return post(`/api/external-models/items/${id}/logical-model`, {});
}

export function createLogicalModelsFromExternalItems(params) {
  return post("/api/external-models/items/logical-models", params || {});
}

export function deleteExternalModelItem(id) {
  return del(`/api/external-models/items/${id}`);
}

export function deleteExternalModelItems(ids) {
  return post("/api/external-models/items/batch-delete", ids);
}
