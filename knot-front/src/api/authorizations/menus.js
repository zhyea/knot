import { del, get, post, put } from "../http";

export function listAuthorizationMenus(params) {
  return get("/api/system/authorizations/menus", { params });
}

export function createAuthorizationMenu(data) {
  return post("/api/system/authorizations/menus", data);
}

export function updateAuthorizationMenu(id, data) {
  return put(`/api/system/authorizations/menus/${id}`, data);
}

export function updateAuthorizationMenuStatus(id, enabled) {
  return put(`/api/system/authorizations/menus/${id}/status`, { enabled });
}

export function deleteAuthorizationMenu(id) {
  return del(`/api/system/authorizations/menus/${id}`);
}
