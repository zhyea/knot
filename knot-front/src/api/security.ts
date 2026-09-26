import { postQuery, post, put } from "./http";
import type { Dict } from "../types";

export function getSecurityOverview() {
  return postQuery("/api/security/overview");
}

export function updateSecurityPolicy(payload: Dict) {
  return put("/api/security/policies", payload);
}

export function listSecurityAlerts(params: Dict) {
  return postQuery("/api/security/alerts", params);
}

export function evictCache(payload: Dict) {
  return post("/api/security/cache/evict", payload);
}
