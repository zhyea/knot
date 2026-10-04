import {postQuery, post, put, get, del, postEventStream} from "./http";
import type {Dict} from "@/types";
import type {AxiosRequestConfig} from "axios";

export function listRoutingRules(params: Dict) {
  return postQuery("/api/routing-rules/list", params);
}

export function listRoutingConsumers(params: Dict) {
  return postQuery("/api/routing-consumers/list", params);
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

export function testRoutingRule(id: number | string, payload: Dict, config: AxiosRequestConfig) {
  return post(`/api/routing-rules/${id}/test`, payload, config);
}

/**
 * 流式路由规则测试：以 fetch 发起 SSE 请求，返回原始 Response 供增量解析。
 * 与 testRoutingRule 的区别仅在传输方式，请求体结构完全一致。
 */
export function testRoutingRuleStream(
  id: number | string,
  payload: Dict,
  signal?: AbortSignal
) {
  return postEventStream(`/api/routing-rules/${id}/test/stream`, payload, signal);
}

/** 调试协议能力：gatewayPath / hint / promptField（默认请求体不再硬编码，改由预设请求维护） */
export function listDebugCapabilities() {
  return get("/api/routing-rules/debug-capabilities");
}

// ===================== 预设请求（路由调试请求用例） =====================

export function listTestRequestPresets(params: Dict) {
  return postQuery("/api/test-request-presets/list", params);
}

export function listTestRequestPresetOptions() {
  return get("/api/test-request-presets/options");
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

