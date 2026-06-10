import { del, get, post, put } from "../http";

export function listAuthorizationApiBindings(params) {
  return get("/api/system/authorizations/api-bindings", { params });
}

export function createAuthorizationApiBinding(data) {
  return post("/api/system/authorizations/api-bindings", data);
}

export function updateAuthorizationApiBinding(id, data) {
  return put(`/api/system/authorizations/api-bindings/${id}`, data);
}

export function updateAuthorizationApiBindingStatus(id, enabled) {
  return put(`/api/system/authorizations/api-bindings/${id}/status`, { enabled });
}

export function deleteAuthorizationApiBinding(id) {
  return del(`/api/system/authorizations/api-bindings/${id}`);
}
