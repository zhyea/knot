import { postQuery, post, put } from "./http";

export function listUsers(params) {
  return postQuery("/api/users", params);
}

export function createUser(payload) {
  return post("/api/users/create", payload);
}

export function updateUserStatus(id, payload) {
  return put(`/api/users/${id}/status`, payload);
}
