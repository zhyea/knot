import {del, get, post, postQuery, put} from "./http";
import type {Dict} from "@/types";

export function listModelPools(params: Dict) {
  return postQuery("/api/model-pools/list", params);
}

export function getModelPool(id: number | string) {
  return get(`/api/model-pools/${id}`);
}

export function checkModelPoolCode(code: string, excludeId: number | string | null) {
  return get("/api/model-pools/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

export function createModelPool(payload: Dict) {
  return post("/api/model-pools", payload);
}

export function updateModelPool(id: number | string, payload: Dict) {
  return put(`/api/model-pools/${id}`, payload);
}

export function updateModelPoolStatus(id: number | string, enabled: boolean) {
  return put(`/api/model-pools/${id}/status`, {enabled});
}

export function deleteModelPool(id: number | string) {
  return del(`/api/model-pools/${id}`);
}
