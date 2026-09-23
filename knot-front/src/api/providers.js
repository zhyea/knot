import { postQuery, post, put, get } from "./http";

export function listProviderAccounts(params) {
  return postQuery("/api/provider-accounts/list", params);
}

export function getProviderAccount(id) {
  return get(`/api/provider-accounts/${id}`);
}

export function getProviderAccountOption(id) {
  return get(`/api/provider-accounts/options/${id}`);
}

export function suggestProviderAccountCode() {
  return get("/api/provider-accounts/suggest-code");
}

export function checkProviderAccountCode(code, excludeId) {
  return get("/api/provider-accounts/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createProviderAccount(payload) {
  return post("/api/provider-accounts", payload);
}

export function updateProviderAccount(id, payload) {
  return put(`/api/provider-accounts/${id}`, payload);
}

export function updateProviderAccountStatus(id, enabled) {
  return put(`/api/provider-accounts/${id}/status`, { enabled });
}

export const listProviders = listProviderAccounts;
export const getProvider = getProviderAccount;
export const suggestProviderCode = suggestProviderAccountCode;
export const checkProviderCode = checkProviderAccountCode;
export const createProvider = createProviderAccount;
export const updateProvider = updateProviderAccount;
export const updateProviderStatus = updateProviderAccountStatus;

export function listDiscountPolicies(providerAccountId, params) {
  return postQuery(`/api/provider-accounts/${providerAccountId}/discount-policies/list`, params);
}

export function createDiscountPolicy(providerAccountId, payload) {
  return post(`/api/provider-accounts/${providerAccountId}/discount-policies`, payload);
}

export function updateDiscountPolicy(providerAccountId, policyId, payload) {
  return put(`/api/provider-accounts/${providerAccountId}/discount-policies/${policyId}`, payload);
}
