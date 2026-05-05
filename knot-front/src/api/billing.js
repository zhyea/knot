import { postQuery, post } from "./http";

export function listBillingRules(params) {
  return postQuery("/api/billing/rules/list", params);
}

export function createBillingRule(payload) {
  return post("/api/billing/rules", payload);
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
