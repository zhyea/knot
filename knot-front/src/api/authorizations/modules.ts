import { del, get, post, put } from "../http";
import type { Dict } from "@/types";

export function listAuthorizationModules(params: Dict) {
  return get("/api/system/authorizations/modules", { params });
}

export function createAuthorizationModule(data: Dict) {
  return post("/api/system/authorizations/modules", data);
}

export function updateAuthorizationModule(id: number | string, data: Dict) {
  return put(`/api/system/authorizations/modules/${id}`, data);
}

export function updateAuthorizationModuleStatus(id: number | string, enabled: boolean) {
  return put(`/api/system/authorizations/modules/${id}/status`, { enabled });
}

export function deleteAuthorizationModule(id: number | string) {
  return del(`/api/system/authorizations/modules/${id}`);
}
