import { get, post, put } from "./http";

export function getSecurityOverview() {
  return get("/api/security/overview");
}

export function updateSecurityPolicy(payload) {
  return put("/api/security/policies", payload);
}

export function listSecurityAlerts() {
  return get("/api/security/alerts");
}

export function evictCache(payload) {
  return post("/api/security/cache/evict", payload);
}
