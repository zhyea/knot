import {postQuery, post, put, get, del} from "./http";
import type {Dict} from "@/types";

export function listModels(params: Dict) {
  return postQuery("/api/models/list", params);
}

export function getModel(id: number | string) {
  return get(`/api/models/${id}`);
}

export function checkModelCode(code: string, excludeId: number | string | null) {
  return get("/api/models/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
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
  return put(`/api/models/${id}/status`, {enabled});
}

/** 逻辑删除供应商模型（被路由规则引用时后端返回 409）；不物理删除，可恢复 */
export function deleteModel(id: number | string) {
  return del(`/api/models/${id}`);
}

/**
 * 恢复已逻辑删除的供应商模型。
 * model_code 唯一性按物理行判定（uk_models_code 不区分 is_deleted），删除后同 model_code
 * 无法新建，只能恢复；管理列表传 includeDeleted=true 才能看到已删除行。
 */
export function restoreModel(id: number | string) {
  return put(`/api/models/${id}/restore`);
}

/* ==================== 折扣策略（绑定供应商模型 model_code） ==================== */

export function listDiscountPolicies(modelCode: string, params: Dict = {}) {
  return postQuery(`/api/models/${modelCode}/discount-policies/list`, params);
}

export function createDiscountPolicy(modelCode: string, payload: Dict) {
  return post(`/api/models/${modelCode}/discount-policies`, payload);
}

export function updateDiscountPolicy(modelCode: string, policyId: number | string, payload: Dict) {
  return put(`/api/models/${modelCode}/discount-policies/${policyId}`, payload);
}
