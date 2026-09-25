import { postQuery, post, put, del, get } from "./http";

export function listEnumConfigs(params) {
  return postQuery("/api/system/enums/list", params);
}

/** 枚举分类聚合（首页列表） */
export function listEnumCategorySummaries(params) {
  return postQuery("/api/system/enums/category-summaries", params || {});
}

/** 某分类下全部枚举项 */
export function listEnumItemsByCategory(category) {
  return get(`/api/system/enums/items/${encodeURIComponent(category)}`);
}

/** 某分类下枚举相关操作日志 */
export function listEnumOperationLogs(category) {
  return get(`/api/system/enums/operation-logs/${encodeURIComponent(category)}`);
}

export function listEnumCategories() {
  return postQuery("/api/system/enums/categories", {});
}

export function createEnumConfig(payload) {
  return post("/api/system/enums", payload);
}

export function updateEnumConfig(id, payload) {
  return put(`/api/system/enums/${id}`, payload);
}

export function deleteEnumConfig(id) {
  return del(`/api/system/enums/${id}`);
}

/** @deprecated 使用 listEnumItemsByCategory */
export function listEnumsByCategory(category) {
  return listEnumItemsByCategory(category);
}

/**
 * 代码枚举全集：GET /api/common/enums，返回 { 枚举键: { code: label } }。
 * 目前仅包含已从 DB 迁移到后端的 ModelTypeEnum、ModelApiProtocolEnum，
 * 其余枚举分类仍走 /api/system/enums（ks_enum_configs）。
 */
export function listCommonEnums() {
  return get("/api/common/enums");
}
