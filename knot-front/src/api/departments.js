import { del, get, post, postQuery, put } from "./http";

export function listDepartments(params) {
  return postQuery("/api/system/departments/list", params);
}

export function getDepartmentTree() {
  return get("/api/system/departments/tree");
}

export function createDepartment(payload) {
  return post("/api/system/departments", payload);
}

export function updateDepartment(id, payload) {
  return put(`/api/system/departments/${id}`, payload);
}

export function updateDepartmentStatus(id, payload) {
  return put(`/api/system/departments/${id}/status`, payload);
}

export function deleteDepartment(id) {
  return del(`/api/system/departments/${id}`);
}
