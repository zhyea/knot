import {del, get, post, postQuery, put} from "@/api/http";
import type {Dict, PageResult} from "@/types";

export function listAuthorizationRoles(params: Dict) {
  return postQuery<PageResult<Dict>>("/api/system/authorizations/roles/list", params);
}

export function getRoleAuthorizationSnapshot(roleId: number | string) {
  return get<Dict>(`/api/system/authorizations/roles/${roleId}/snapshot`);
}

/** 角色详情：编辑弹窗打开时按 id 精确取，不复用列表行数据。 */
export function getAuthorizationRole(id: number | string) {
  return get<Dict>(`/api/system/authorizations/roles/${id}`);
}

export function createAuthorizationRole(data: Dict) {
  return post<Dict>("/api/system/authorizations/roles", data);
}

export function updateAuthorizationRole(id: number | string, data: Dict) {
  return put<Dict>(`/api/system/authorizations/roles/${id}`, data);
}

export function deleteAuthorizationRole(id: number | string) {
  return del<Dict>(`/api/system/authorizations/roles/${id}`);
}

export function saveRolePermissions(roleId: number | string, permissionIds: unknown[]) {
  return put<Dict>(`/api/system/authorizations/roles/${roleId}/permissions`, permissionIds);
}
