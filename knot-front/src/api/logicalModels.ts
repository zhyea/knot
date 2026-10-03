import {del, get, post, postQuery, put} from "./http";
import type {Dict} from "@/types";

export function listLogicalModels(params: Dict) {
  return postQuery("/api/logical-models/list", params);
}

export function getLogicalModel(id: number | string) {
  return get(`/api/logical-models/${id}`);
}

export function checkLogicalModelCode(code: string, excludeId: number | string | null) {
  return get("/api/logical-models/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

export function createLogicalModel(payload: Dict) {
  return post("/api/logical-models", payload);
}

export function updateLogicalModel(id: number | string, payload: Dict) {
  return put(`/api/logical-models/${id}`, payload);
}

export function updateLogicalModelStatus(id: number | string, enabled: boolean) {
  return put(`/api/logical-models/${id}/status`, {enabled});
}

export function deleteLogicalModel(id: number | string) {
  return del(`/api/logical-models/${id}`);
}

/**
 * 恢复已逻辑删除的统一模型。
 * 编码唯一性按物理行判定（uk_logical_models_code 不区分 is_deleted），删除后同 model_code
 * 无法新建，只能恢复；管理列表传 includeDeleted=true 才能看到已删除行。
 */
export function restoreLogicalModel(id: number | string) {
  return put(`/api/logical-models/${id}/restore`);
}
