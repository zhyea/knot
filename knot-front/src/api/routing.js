import { postQuery, post, put } from "./http";

export function listRoutingRules(params) {
  return postQuery("/api/routing-rules/list", params);
}

export function createRoutingRule(payload) {
  return post("/api/routing-rules", payload);
}

export function updateRoutingRule(id, payload) {
  return put(`/api/routing-rules/${id}`, payload);
}

export function testRoutingRule(id, payload) {
  return post(`/api/routing-rules/${id}/test`, payload);
}

export function listSwitchLogs(params) {
  return postQuery("/api/routing-rules/switch-logs", params);
}
