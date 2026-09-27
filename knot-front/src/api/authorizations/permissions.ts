import { del, get, post, put } from "../http";
import type { Dict } from "@/types";

export function listAuthorizationPermissions(params: Dict) {
  return get("/api/system/authorizations/permissions", { params });
}

export function createAuthorizationPermission(data: Dict) {
  return post("/api/system/authorizations/permissions", data);
}

export function updateAuthorizationPermission(id: number | string, data: Dict) {
  return put(`/api/system/authorizations/permissions/${id}`, data);
}

export function updateAuthorizationPermissionStatus(id: number | string, enabled: boolean) {
  return put(`/api/system/authorizations/permissions/${id}/status`, { enabled });
}

export function deleteAuthorizationPermission(id: number | string) {
  return del(`/api/system/authorizations/permissions/${id}`);
}
