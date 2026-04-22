import { get, post, put } from "./http";

export function listProviders() {
  return get("/api/providers");
}

export function createProvider(payload) {
  return post("/api/providers", payload);
}

export function updateProvider(id, payload) {
  return put(`/api/providers/${id}`, payload);
}

export function listDiscountPolicies(providerId) {
  return get(`/api/providers/${providerId}/discount-policies`);
}

export function createDiscountPolicy(providerId, payload) {
  return post(`/api/providers/${providerId}/discount-policies`, payload);
}

export function updateDiscountPolicy(providerId, policyId, payload) {
  return put(`/api/providers/${providerId}/discount-policies/${policyId}`, payload);
}
