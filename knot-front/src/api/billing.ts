import {postQuery, post, put, del, get} from "./http";
import type {Dict} from "@/types";

export function listBillingRules(params: Dict) {
  return postQuery("/api/billing/rules", params);
}

/** 计费模式能力：supportedUnits / defaultUnit / defaultItemType */
export function listModeCapabilities() {
  return get("/api/billing/mode-capabilities");
}

export function createBillingRule(payload: Dict) {
  return post("/api/billing", payload);
}

export function updateBillingRule(id: number | string, payload: Dict) {
  return put(`/api/billing/rules/${id}`, payload);
}

export function updateBillingRuleStatus(id: number | string, enabled: boolean) {
  return put(`/api/billing/rules/${id}/status`, {enabled});
}

export function deleteBillingRule(id: number | string) {
  return del(`/api/billing/rules/${id}`);
}

export function runReconciliation(payload: Dict) {
  return post("/api/billing/reconciliation", payload);
}
