import { postQuery, post, put, get } from "./http";

export function listRoutingRules(params) {
  return postQuery("/api/routing-rules/list", params);
}

export function listRoutingConsumers(params) {
  return postQuery("/api/routing-consumers/list", params);
}

export function createRoutingConsumer(payload) {
  return post("/api/routing-consumers", payload);
}

export function updateRoutingConsumer(id, payload) {
  return put(`/api/routing-consumers/${id}`, payload);
}

export function rotateRoutingConsumerSecret(id) {
  return post(`/api/routing-consumers/${id}/rotate-secret`);
}

export function checkRoutingConsumerCode(code, excludeId) {
  return get("/api/routing-consumers/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createRoutingRule(payload) {
  return post("/api/routing-rules", payload);
}

export function updateRoutingRule(id, payload) {
  return put(`/api/routing-rules/${id}`, payload);
}

export function checkRoutingRuleCode(code, excludeId) {
  return get("/api/routing-rules/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function testRoutingRule(id, payload) {
  return post(`/api/routing-rules/${id}/test`, payload);
}

