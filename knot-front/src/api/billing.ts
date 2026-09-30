import {postQuery, post, put, del, get} from "./http";
import type {Dict} from "@/types";

export function listBillingRules(params: Dict) {
  return postQuery("/api/billing/rules", params);
}

/** 计费模式能力：supportedUnits / defaultUnit / defaultItemType */
export function listModeCapabilities() {
  return get("/api/billing/mode-capabilities");
}

/** 计费报表汇总（配置维度）：规则状态计数 + 供应商/模式/方案/币种分布 */
export function getBillingReportSummary() {
  return get("/api/billing/report/summary");
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

/**
 * 计费规则试算：给定发生时刻与用量，返回命中相位/倍率与实际单价。
 *
 * <p>后端复用与网关热路径同一个判定器与价格解析链，因此这里看到的结果就是真实计费结果；
 * {@code occurredAt} 支持 ISO instant（{@code 2026-09-30T02:30:00Z}）与带偏移的时间串。
 */
export function previewBillingRule(id: number | string, payload: Dict) {
  return post(`/api/billing/rules/${id}/preview`, payload);
}

export function deleteBillingRule(id: number | string) {
  return del(`/api/billing/rules/${id}`);
}

export function runReconciliation(payload: Dict) {
  return post("/api/billing/reconciliation", payload);
}
