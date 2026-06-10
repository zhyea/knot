import { del, get, post, put } from "../http";

export function listAuthorizationPermissions(params) {
  return get("/api/system/authorizations/permissions", { params });
}

export function createAuthorizationPermission(data) {
  return post("/api/system/authorizations/permissions", data);
}

export function updateAuthorizationPermission(id, data) {
  return put(`/api/system/authorizations/permissions/${id}`, data);
}

export function updateAuthorizationPermissionStatus(id, enabled) {
  return put(`/api/system/authorizations/permissions/${id}/status`, { enabled });
}

export function deleteAuthorizationPermission(id) {
  return del(`/api/system/authorizations/permissions/${id}`);
}
