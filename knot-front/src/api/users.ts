import { postQuery, post, put } from "./http";
import type { Dict } from "../types";

export function listUsers(params: Dict) {
  return postQuery("/api/users", params);
}

export function createUser(payload: Dict) {
  return post("/api/users/create", payload);
}

export function updateUserStatus(id: number | string, payload: Dict) {
  return put(`/api/users/${id}/status`, payload);
}

export function updateUser(id: number | string, payload: Dict) {
  return put(`/api/users/${id}`, payload);
}

export function resetUserPassword(id: number | string) {
  return put(`/api/users/${id}/reset-password`);
}
