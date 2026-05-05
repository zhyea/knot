import { postQuery, post } from "./http";

export function listGrayPlans(params) {
  return postQuery("/api/release/gray-plans", params);
}

export function createGrayPlan(payload) {
  return post("/api/release/gray-plans", payload);
}

export function publishGrayPlan(id) {
  return post(`/api/release/gray-plans/${id}/publish`);
}

export function rollbackGrayPlan(id) {
  return post(`/api/release/gray-plans/${id}/rollback`);
}
