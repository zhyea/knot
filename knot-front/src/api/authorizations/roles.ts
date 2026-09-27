import {del, get, post, postQuery, put} from "@/api/http";
import type {Dict} from "@/types";

export function listAuthorizationRoles(params: Dict) {
  return postQuery("/api/system/authorizations/roles/list", params);
}

export function getRoleAuthorizationSnapshot(roleId: number | string) {
  return get(`/api/system/authorizations/roles/${roleId}/snapshot`);
}

export function createAuthorizationRole(data: Dict) {
  return post("/api/system/authorizations/roles", data);
}

export function updateAuthorizationRole(id: number | string, data: Dict) {
  return put(`/api/system/authorizations/roles/${id}`, data);
}

export function deleteAuthorizationRole(id: number | string) {
  return del(`/api/system/authorizations/roles/${id}`);
}

export function saveRolePermissions(roleId: number | string, permissionIds: unknown[]) {
  return put(`/api/system/authorizations/roles/${roleId}/permissions`, permissionIds);
}
