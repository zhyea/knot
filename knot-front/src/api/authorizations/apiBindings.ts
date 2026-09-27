import { del, get, post, put } from "../http";
import type { Dict } from "@/types";

export function listAuthorizationApiBindings(params: Dict) {
  return get("/api/system/authorizations/api-bindings", { params });
}

export function createAuthorizationApiBinding(data: Dict) {
  return post("/api/system/authorizations/api-bindings", data);
}

export function updateAuthorizationApiBinding(id: number | string, data: Dict) {
  return put(`/api/system/authorizations/api-bindings/${id}`, data);
}

export function updateAuthorizationApiBindingStatus(id: number | string, enabled: boolean) {
  return put(`/api/system/authorizations/api-bindings/${id}/status`, { enabled });
}

export function deleteAuthorizationApiBinding(id: number | string) {
  return del(`/api/system/authorizations/api-bindings/${id}`);
}
