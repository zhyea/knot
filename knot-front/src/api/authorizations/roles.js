import { del, get, post, postQuery, put } from "../http";

export function listAuthorizationRoles(params) {
  return postQuery("/api/system/authorizations/roles/list", params);
}

export function getRoleAuthorizationSnapshot(roleId) {
  return get(`/api/system/authorizations/roles/${roleId}/snapshot`);
}

export function createAuthorizationRole(data) {
  return post("/api/system/authorizations/roles", data);
}

export function updateAuthorizationRole(id, data) {
  return put(`/api/system/authorizations/roles/${id}`, data);
}

export function deleteAuthorizationRole(id) {
  return del(`/api/system/authorizations/roles/${id}`);
}

export function saveRolePermissions(roleId, permissionIds) {
  return put(`/api/system/authorizations/roles/${roleId}/permissions`, permissionIds);
}
