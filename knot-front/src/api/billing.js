import { postQuery, post, put, del, get } from "./http";

export function listBillingRules(params) {
  return postQuery("/api/billing/rules", params);
}

/** 计费模式能力：supportedUnits / defaultUnit / defaultItemType */
export function listModeCapabilities() {
  return get("/api/billing/mode-capabilities");
}

export function createBillingRule(payload) {
  return post("/api/billing", payload);
}

export function updateBillingRule(id, payload) {
  return put(`/api/billing/rules/${id}`, payload);
}

export function updateBillingRuleStatus(id, enabled) {
  return put(`/api/billing/rules/${id}/status`, { enabled });
}

export function deleteBillingRule(id) {
  return del(`/api/billing/rules/${id}`);
}

export function runReconciliation(payload) {
  return post("/api/billing/reconciliation", payload);
}
