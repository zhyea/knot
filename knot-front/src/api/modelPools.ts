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

/**
 * 恢复已逻辑删除的模型池。
 * 编码唯一性按物理行判定（uk_model_pools_code 不区分 is_deleted），删除后同 pool_code
 * 无法新建，只能恢复；管理列表传 includeDeleted=true 才能看到已删除行。
 */
export function restoreModelPool(id: number | string) {
  return put(`/api/model-pools/${id}/restore`);
}
