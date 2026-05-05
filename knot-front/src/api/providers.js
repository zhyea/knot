import { postQuery, post, put } from "./http";

export function listProviders(params) {
  return postQuery("/api/providers", params);
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
