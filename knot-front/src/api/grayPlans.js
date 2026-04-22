import { get, post } from "./http";

export function listGrayPlans() {
  return get("/api/release/gray-plans");
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
