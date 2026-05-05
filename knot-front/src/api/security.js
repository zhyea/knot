import { postQuery, post, put } from "./http";

export function getSecurityOverview() {
  return postQuery("/api/security/overview");
}

export function updateSecurityPolicy(payload) {
  return put("/api/security/policies", payload);
}

export function listSecurityAlerts(params) {
  return postQuery("/api/security/alerts", params);
}

export function evictCache(payload) {
  return post("/api/security/cache/evict", payload);
}
