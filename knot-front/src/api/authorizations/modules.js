import { del, get, post, put } from "../http";

export function listAuthorizationModules(params) {
  return get("/api/system/authorizations/modules", { params });
}

export function createAuthorizationModule(data) {
  return post("/api/system/authorizations/modules", data);
}

export function updateAuthorizationModule(id, data) {
  return put(`/api/system/authorizations/modules/${id}`, data);
}

export function updateAuthorizationModuleStatus(id, enabled) {
  return put(`/api/system/authorizations/modules/${id}/status`, { enabled });
}

export function deleteAuthorizationModule(id) {
  return del(`/api/system/authorizations/modules/${id}`);
}
