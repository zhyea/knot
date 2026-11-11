import {del, get, post, put} from "@/api/http";
import type {Dict} from "@/types";

export function listAuthorizationApiBindings(params: Dict) {
  return get<Dict[]>("/api/system/authorizations/api-bindings", { params });
}

export function createAuthorizationApiBinding(data: Dict) {
  return post<Dict>("/api/system/authorizations/api-bindings", data);
}

export function updateAuthorizationApiBinding(id: number | string, data: Dict) {
  return put<Dict>(`/api/system/authorizations/api-bindings/${id}`, data);
}

export function updateAuthorizationApiBindingStatus(id: number | string, enabled: boolean) {
  return put<Dict>(`/api/system/authorizations/api-bindings/${id}/status`, { enabled });
}

export function deleteAuthorizationApiBinding(id: number | string) {
  return del<Dict>(`/api/system/authorizations/api-bindings/${id}`);
}
