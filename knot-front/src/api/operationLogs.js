import { postQuery, get } from "./http";

/**
 * 分页查询操作日志
 */
export function listOperationLogs(params) {
  return postQuery("/api/operation-logs/list", params);
}

/**
 * 根据ID查询操作日志详情
 */
export function getOperationLogDetail(id) {
  return get(`/api/operation-logs/${id}`);
}

/**
 * 根据模块查询操作日志
 */
export function getOperationLogsByModule(module, params) {
  return get(`/api/operation-logs/module/${module}`, { params });
}

/**
 * 根据操作人查询操作日志
 */
export function getOperationLogsByOperator(operatorId, params) {
  return get(`/api/operation-logs/operator/${operatorId}`, { params });
}

/**
 * 根据实体查询操作日志
 */
export function getOperationLogsByEntity(entityType, entityId, params) {
  return get(`/api/operation-logs/entity/${entityType}/${entityId}`, { params });
}
