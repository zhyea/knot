import {del, get, post, postQuery} from "./http";
import type {Dict} from "@/types";

export function listExternalModelSources() {
  return get("/api/external-models/sources");
}

export function listExternalModelItems(params: Dict) {
  return postQuery("/api/external-models/items/list", params);
}

export function getExternalModelItem(id: number | string) {
  return get(`/api/external-models/items/${id}`);
}

export function syncExternalModelSource(sourceCode: string) {
  return post(`/api/external-models/sources/${sourceCode}/sync`, {});
}

export function createLogicalModelFromExternalItem(id: number | string) {
  return post(`/api/external-models/items/${id}/logical-model`, {});
}

export function createLogicalModelsFromExternalItems(params: Dict) {
  return post("/api/external-models/items/logical-models", params || {});
}

export function deleteExternalModelItem(id: number | string) {
  return del(`/api/external-models/items/${id}`);
}

export function deleteExternalModelItems(ids: (number | string)[]) {
  return post("/api/external-models/items/batch-delete", ids);
}

export function setExternalModelIgnored(id: number | string, ignored: boolean) {
  return post(`/api/external-models/items/${id}/ignored?ignored=${ignored}`, {});
}
