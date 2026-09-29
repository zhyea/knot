import {postQuery, get} from "./http";
import type {Dict} from "@/types";
import type {AxiosRequestConfig} from "axios";

/**
 * 分页查询操作日志
 */
export function listOperationLogs(params: Dict) {
  return postQuery("/api/operation-logs/list", params);
}

/**
 * 根据ID查询操作日志详情
 */
export function getOperationLogDetail(id: number | string) {
  return get(`/api/operation-logs/${id}`);
}

/**
 * 根据模块查询操作日志
 */
export function getOperationLogsByModule(module: string, params: Dict) {
  return get(`/api/operation-logs/module/${module}`, {params});
}

/**
 * 根据操作人查询操作日志
 */
export function getOperationLogsByOperator(operatorId: number | string, params: Dict) {
  return get(`/api/operation-logs/operator/${operatorId}`, {params});
}

/**
 * 根据实体查询操作日志
 */
export function getOperationLogsByEntity(entityType: string, entityId: number | string, config: AxiosRequestConfig) {
  const typeSeg = encodeURIComponent(entityType);
  return get(`/api/operation-logs/entity/${typeSeg}/${entityId}`, config);
}

/** 某用户（entity_type=User）在 user 模块下的操作日志 */
export function listUserOperationLogs(userId: number | string) {
  return getOperationLogsByEntity("User", userId, {params: {module: "user"}});
}

/** 某供应商信息（entity_type=ProviderProfile）在 provider-profile 模块下的操作日志 */
export function listProviderProfileOperationLogs(id: number | string) {
  return getOperationLogsByEntity("ProviderProfile", id, {params: {module: "provider-profile"}});
}

/** 某供应商（entity_type=Provider）在 provider 模块下的操作日志 */
export function listProviderOperationLogs(providerId: number | string) {
  return getOperationLogsByEntity("Provider", providerId, {params: {module: "provider"}});
}

/** 某模型（entity_type=Model）在 model 模块下的操作日志 */
export function listModelOperationLogs(modelId: number | string) {
  return getOperationLogsByEntity("Model", modelId, {params: {module: "model"}});
}

/** 某统一模型（entity_type=LogicalModel）在 logical-model 模块下的操作日志 */
export function listLogicalModelOperationLogs(logicalModelId: number | string) {
  return getOperationLogsByEntity("LogicalModel", logicalModelId, {params: {module: "logical-model"}});
}

/** 某应用（entity_type=App）在 app 模块下的操作日志 */
export function listAppOperationLogs(appId: number | string) {
  return getOperationLogsByEntity("App", appId, {params: {module: "app"}});
}

/** 某部门（entity_type=Department）在 department 模块下的操作日志 */
export function listDepartmentOperationLogs(departmentId: number | string) {
  return getOperationLogsByEntity("Department", departmentId, {params: {module: "department"}});
}

/** 某路由规则（entity_type=RoutingRule）在 routing 模块下的操作日志 */
export function listRoutingRuleOperationLogs(ruleId: number | string) {
  return getOperationLogsByEntity("RoutingRule", ruleId, {params: {module: "routing"}});
}

export function listBillingRuleOperationLogs(ruleId: number | string) {
  return getOperationLogsByEntity("BillingRule", ruleId, {params: {module: "billing"}});
}
