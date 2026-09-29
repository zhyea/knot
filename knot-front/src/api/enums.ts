import {postQuery, post, put, del, get} from "./http";
import type {Dict} from "@/types";

export function listEnumConfigs(params: Dict) {
  return postQuery("/api/system/enums/list", params);
}

/** 枚举分类聚合（首页列表） */
export function listEnumCategorySummaries(params: Dict) {
  return postQuery("/api/system/enums/category-summaries", params || {});
}

/** 某分类下全部枚举项 */
export function listEnumItemsByCategory(category: string) {
  return get(`/api/system/enums/items/${encodeURIComponent(category)}`);
}

/** 某分类下枚举相关操作日志 */
export function listEnumOperationLogs(category: string) {
  return get(`/api/system/enums/operation-logs/${encodeURIComponent(category)}`);
}

export function listEnumCategories() {
  return postQuery("/api/system/enums/categories", {});
}

export function createEnumConfig(payload: Dict) {
  return post("/api/system/enums", payload);
}

export function updateEnumConfig(id: number | string, payload: Dict) {
  return put(`/api/system/enums/${id}`, payload);
}

export function deleteEnumConfig(id: number | string) {
  return del(`/api/system/enums/${id}`);
}

/**
 * 代码枚举全集：GET /api/common/enums，返回 { 枚举键: { code: label } }。
 * 目前包含已从 DB 迁移到后端的 ModelTypeEnum、ModelApiProtocolEnum、BillingModeEnum、
 * PricingPlanEnum、EntityStatusEnum、RouteTargetTypeEnum，
 * 其余枚举分类仍走 /api/system/enums（ks_enum_configs）。
 */
export function listCommonEnums() {
  return get("/api/common/enums");
}
