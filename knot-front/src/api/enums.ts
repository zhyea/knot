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
 * 已由后端 EnumOptionRegistry 注册的 17 个代码枚举（前端只读、禁止在 DB 重建）：
 *   ModelTypeEnum、ModelApiProtocolEnum、BillingModeEnum、BillingUnitEnum、
 *   CurrencyCodeEnum、PricingPlanEnum、EntityStatusEnum、RouteTargetTypeEnum、
 *   ModelPoolSelectionStrategyEnum、PluginExtensionPoint、PluginStageCode、PluginScopeType、
 *   OperationLogStatusEnum、ScheduledTaskRunStatusEnum、RoutingTestStatusEnum、
 *   LogicalModelVisibilityEnum、LogicalModelPublishStatusEnum。
 * 其余枚举分类仅剩 4 个仍由 DB 维护且有真实消费方：
 *   scope_type（折扣范围）/ discount_type（折扣类型）/ channel（通知渠道）/ model_family（模型族）。
 * 2026-10-03 已退役 5 个零消费孤儿分类（plugin_source_type / alert_level / risk_level /
 *   plugin_fail_mode / plugin_result_status），见迁移脚本 2026-10-03-retire-orphan-enums.sql。
 */
export function listCommonEnums() {
  return get("/api/common/enums");
}
