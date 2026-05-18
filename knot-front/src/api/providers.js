import { postQuery, post, put, get } from "./http";

export function listProviders(params) {
  return postQuery("/api/providers/list", params);
}

export function getProvider(id) {
  return get(`/api/providers/${id}`);
}

export function suggestProviderCode() {
  return get("/api/providers/suggest-code");
}

export function checkProviderCode(code, excludeId) {
  return get("/api/providers/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createProvider(payload) {
  return post("/api/providers", payload);
}

export function updateProvider(id, payload) {
  return put(`/api/providers/${id}`, payload);
}

export function listDiscountPolicies(providerId, params) {
  return postQuery(`/api/providers/${providerId}/discount-policies/list`, params);
}

export function createDiscountPolicy(providerId, payload) {
  return post(`/api/providers/${providerId}/discount-policies`, payload);
}

export function updateDiscountPolicy(providerId, policyId, payload) {
  return put(`/api/providers/${providerId}/discount-policies/${policyId}`, payload);
}
