import { postQuery, post, put, get } from "./http";
import type { Dict } from "../types";

export function listProviderAccounts(params: Dict) {
  return postQuery("/api/provider-accounts/list", params);
}

export function getProviderAccount(id: number | string) {
  return get(`/api/provider-accounts/${id}`);
}

export function getProviderAccountOption(id: number | string) {
  return get(`/api/provider-accounts/options/${id}`);
}

export function suggestProviderAccountCode() {
  return get("/api/provider-accounts/suggest-code");
}

/** 认证类型选项：code / label / requiredFields，由后端 ProviderCredentialTypeEnum 下发 */
export function listCredentialTypes() {
  return get("/api/provider-accounts/credential-types");
}

export function checkProviderAccountCode(code: string, excludeId: number | string | null) {
  return get("/api/provider-accounts/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createProviderAccount(payload: Dict) {
  return post("/api/provider-accounts", payload);
}

export function updateProviderAccount(id: number | string, payload: Dict) {
  return put(`/api/provider-accounts/${id}`, payload);
}

export function updateProviderAccountStatus(id: number | string, enabled: boolean) {
  return put(`/api/provider-accounts/${id}/status`, { enabled });
}

export const listProviders = listProviderAccounts;
export const getProvider = getProviderAccount;
export const suggestProviderCode = suggestProviderAccountCode;
export const checkProviderCode = checkProviderAccountCode;
export const createProvider = createProviderAccount;
export const updateProvider = updateProviderAccount;
export const updateProviderStatus = updateProviderAccountStatus;

export function listDiscountPolicies(providerAccountId: number | string, params: Dict = {}) {
  return postQuery(`/api/provider-accounts/${providerAccountId}/discount-policies/list`, params);
}

export function createDiscountPolicy(providerAccountId: number | string, payload: Dict) {
  return post(`/api/provider-accounts/${providerAccountId}/discount-policies`, payload);
}

export function updateDiscountPolicy(providerAccountId: number | string, policyId: number | string, payload: Dict) {
  return put(`/api/provider-accounts/${providerAccountId}/discount-policies/${policyId}`, payload);
}
