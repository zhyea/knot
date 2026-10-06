import {postQuery, post, put, get, del, postEventStream, postGatewayJson} from "./http";
import type {Dict} from "@/types";
import type {OptionPage} from "./options";

export function listRoutingRules(params: Dict) {
  return postQuery("/api/routing-rules/list", params);
}

export function listRoutingConsumers(params: Dict) {
  return postQuery("/api/routing-consumers/list", params);
}

/** 消费者详情（含 secretKey）：路由测试按 id 精确取密钥，避免列表批量下发。 */
export function getRoutingConsumer(id: number | string) {
  return get<Dict>(`/api/routing-consumers/${id}`);
}

export function createRoutingConsumer(payload: Dict) {
  return post("/api/routing-consumers", payload);
}

export function updateRoutingConsumer(id: number | string, payload: Dict) {
  return put(`/api/routing-consumers/${id}`, payload);
}

export function updateRoutingConsumerStatus(id: number | string, enabled: boolean) {
  return put(`/api/routing-consumers/${id}/status`, {enabled});
}

export function rotateRoutingConsumerSecret(id: number | string) {
  return post(`/api/routing-consumers/${id}/rotate-secret`);
}

export function checkRoutingConsumerCode(code: string, excludeId: number | string | null) {
  return get("/api/routing-consumers/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

export function createRoutingRule(payload: Dict) {
  return post("/api/routing-rules", payload);
}

export function updateRoutingRule(id: number | string, payload: Dict) {
  return put(`/api/routing-rules/${id}`, payload);
}

export function updateRoutingRuleStatus(id: number | string, enabled: boolean) {
  return put(`/api/routing-rules/${id}/status`, {enabled});
}

export function checkRoutingRuleCode(code: string, excludeId: number | string | null) {
  return get("/api/routing-rules/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

export function testRoutingRule(
  gatewayUrl: string,
  payload: Dict,
  secretKey: string,
  ruleCode: string
) {
  return postGatewayJson(gatewayUrl, payload, secretKey, ruleCode);
}

/**
 * 流式路由规则测试：直接请求网关协议地址，返回原始 Response 供增量解析。
 * 管理端只负责配置/选择目标；网关需要消费者 API Key 和 Rule 头，不能走 /api 管理接口。
 */
export function testRoutingRuleStream(
  gatewayUrl: string,
  payload: Dict,
  secretKey: string,
  ruleCode: string,
  signal?: AbortSignal
) {
  return postEventStream(gatewayUrl, payload, signal, {
    Authorization: `Bearer ${secretKey}`,
    Rule: ruleCode
  });
}

/** 调试协议能力：gatewayPath / hint / promptField（默认请求体不再硬编码，改由预设请求维护） */
export function listDebugCapabilities() {
  return get("/api/routing-rules/debug-capabilities");
}

// ===================== 预设请求（路由调试请求用例） =====================

export function listTestRequestPresets(params: Dict) {
  return postQuery("/api/test-request-presets/list", params);
}

/** 下拉候选：统一 OptionPage 契约（value=id，label=name，code=protocolCode）。 */
export function listTestRequestPresetOptions() {
  return get<OptionPage>("/api/test-request-presets/options");
}

/** 预设详情：选中后按 id 取完整请求体模板（options 不含 requestBody）。 */
export function getTestRequestPreset(id: number | string) {
  return get<Dict>(`/api/test-request-presets/${id}`);
}

export function createTestRequestPreset(payload: Dict) {
  return post("/api/test-request-presets", payload);
}

export function updateTestRequestPreset(id: number | string, payload: Dict) {
  return put(`/api/test-request-presets/${id}`, payload);
}

export function updateTestRequestPresetStatus(id: number | string, enabled: boolean) {
  return put(`/api/test-request-presets/${id}/status`, {enabled});
}

export function deleteTestRequestPreset(id: number | string) {
  return del(`/api/test-request-presets/${id}`);
}
