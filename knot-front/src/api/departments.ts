import { del, get, post, postQuery, put } from "./http";
import type { Dict } from "../types";

export function listDepartments(params: Dict) {
  return postQuery("/api/system/departments/list", params);
}

export function getDepartmentTree() {
  return get("/api/system/departments/tree");
}

export function createDepartment(payload: Dict) {
  return post("/api/system/departments", payload);
}

export function updateDepartment(id: number | string, payload: Dict) {
  return put(`/api/system/departments/${id}`, payload);
}

export function updateDepartmentStatus(id: number | string, payload: Dict) {
  return put(`/api/system/departments/${id}/status`, payload);
}

export function deleteDepartment(id: number | string) {
  return del(`/api/system/departments/${id}`);
}
