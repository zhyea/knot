import {del, get, post, put} from "@/api/http";
import type {Dict} from "@/types";

export function listAuthorizationPermissions(params: Dict) {
  return get<Dict[]>("/api/system/authorizations/permissions", { params });
}

export function createAuthorizationPermission(data: Dict) {
  return post<Dict>("/api/system/authorizations/permissions", data);
}

export function updateAuthorizationPermission(id: number | string, data: Dict) {
  return put<Dict>(`/api/system/authorizations/permissions/${id}`, data);
}

export function updateAuthorizationPermissionStatus(id: number | string, enabled: boolean) {
  return put<Dict>(`/api/system/authorizations/permissions/${id}/status`, { enabled });
}

export function deleteAuthorizationPermission(id: number | string) {
  return del<Dict>(`/api/system/authorizations/permissions/${id}`);
}
