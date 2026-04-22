import { get, post } from "./http";

export function listBillingRules() {
  return get("/api/billing/rules");
}

export function createBillingRule(payload) {
  return post("/api/billing/rules", payload);
}

export function getBillingSummary() {
  return get("/api/billing/summary");
}

export function listBillingDetails() {
  return get("/api/billing/details");
}

export function runReconciliation(payload) {
  return post("/api/billing/reconciliation", payload);
}
