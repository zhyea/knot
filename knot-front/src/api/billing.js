import { postQuery, post, put } from "./http";

export function listBillingRules(params) {
  return postQuery("/api/billing/rules", params);
}

export function createBillingRule(payload) {
  return post("/api/billing", payload);
}

export function updateBillingRule(id, payload) {
  return put(`/api/billing/rules/${id}`, payload);
}

export function getBillingSummary() {
  return postQuery("/api/billing/summary");
}

export function listBillingDetails(params) {
  return postQuery("/api/billing/details", params);
}

export function runReconciliation(payload) {
  return post("/api/billing/reconciliation", payload);
}
