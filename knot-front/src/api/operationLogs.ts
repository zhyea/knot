import {postQuery, get} from "./http";
import type {Dict, Row} from "@/types";

/**
 * 分页查询操作日志（POST）
 */
export function listOperationLogs(params: Dict) {
  return postQuery("/api/operation-logs/list", params);
}

/**
 * 按模块 + 实体维度查询操作日志（POST /api/operation-logs/list）。
 * 返回日志数组，便于直接作为 OperationLogDrawer 的 loadLogs 数据源。
 */
export async function listOperationLogsByEntity(params: {
  module?: string;
  entityType?: string;
  entityId?: number | string;
  operation?: string;
  status?: string;
  keyword?: string;
}): Promise<Row[]> {
  const res = (await listOperationLogs({
    pageNum: 1,
    pageSize: 200,
    ...params
  })) as { list?: Row[] };
  return res?.list ?? [];
}

/** 某用户（entity_type=User）在 user 模块下的操作日志 */
export function listUserOperationLogs(userId: number | string) {
  return listOperationLogsByEntity({ module: "user", entityType: "User", entityId: userId });
}

/** 某供应商信息（entity_type=ProviderProfile）在 provider-profile 模块下的操作日志 */
export function listProviderProfileOperationLogs(id: number | string) {
  return listOperationLogsByEntity({ module: "provider-profile", entityType: "ProviderProfile", entityId: id });
}

/** 某供应商（entity_type=Provider）在 provider 模块下的操作日志 */
export function listProviderOperationLogs(providerId: number | string) {
  return listOperationLogsByEntity({ module: "provider", entityType: "Provider", entityId: providerId });
}

/** 某模型（entity_type=Model）在 model 模块下的操作日志 */
export function listModelOperationLogs(modelId: number | string) {
  return listOperationLogsByEntity({ module: "model", entityType: "Model", entityId: modelId });
}

/** 某统一模型（entity_type=LogicalModel）在 logical-model 模块下的操作日志 */
export function listLogicalModelOperationLogs(logicalModelId: number | string) {
  return listOperationLogsByEntity({ module: "logical-model", entityType: "LogicalModel", entityId: logicalModelId });
}

/** 某模型池（entity_type=ModelPool）在 model-pool 模块下的操作日志 */
export function listModelPoolOperationLogs(poolId: number | string) {
  return listOperationLogsByEntity({ module: "model-pool", entityType: "ModelPool", entityId: poolId });
}

/** 某应用（entity_type=App）在 app 模块下的操作日志 */
export function listAppOperationLogs(appId: number | string) {
  return listOperationLogsByEntity({ module: "app", entityType: "App", entityId: appId });
}

/** 某部门（entity_type=Department）在 department 模块下的操作日志 */
export function listDepartmentOperationLogs(departmentId: number | string) {
  return listOperationLogsByEntity({ module: "department", entityType: "Department", entityId: departmentId });
}

/** 某路由规则（entity_type=RoutingRule）在 routing 模块下的操作日志 */
export function listRoutingRuleOperationLogs(ruleId: number | string) {
  return listOperationLogsByEntity({ module: "routing", entityType: "RoutingRule", entityId: ruleId });
}

/** 某消费者（entity_type=RoutingConsumer）在 routing 模块下的操作日志 */
export function listRoutingConsumerOperationLogs(consumerId: number | string) {
  return listOperationLogsByEntity({ module: "routing", entityType: "RoutingConsumer", entityId: consumerId });
}

/** 某预设请求（entity_type=TestRequestPreset）在 routing 模块下的操作日志 */
export function listTestRequestPresetOperationLogs(presetId: number | string) {
  return listOperationLogsByEntity({ module: "routing", entityType: "TestRequestPreset", entityId: presetId });
}

/** 某计费规则（entity_type=BillingRule）在 billing 模块下的操作日志 */
export function listBillingRuleOperationLogs(ruleId: number | string) {
  return listOperationLogsByEntity({ module: "billing", entityType: "BillingRule", entityId: ruleId });
}
