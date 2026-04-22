import { get, post, put } from "./http";

export function listRoutingRules() {
  return get("/api/routing-rules");
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

export function listSwitchLogs() {
  return get("/api/routing-rules/switch-logs");
}
