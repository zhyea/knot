import { del, get, post, put } from "../http";
import type { Dict } from "../../types";

export function listAuthorizationMenus(params: Dict) {
  return get("/api/system/authorizations/menus", { params });
}

export function createAuthorizationMenu(data: Dict) {
  return post("/api/system/authorizations/menus", data);
}

export function updateAuthorizationMenu(id: number | string, data: Dict) {
  return put(`/api/system/authorizations/menus/${id}`, data);
}

export function updateAuthorizationMenuStatus(id: number | string, enabled: boolean) {
  return put(`/api/system/authorizations/menus/${id}/status`, { enabled });
}

export function deleteAuthorizationMenu(id: number | string) {
  return del(`/api/system/authorizations/menus/${id}`);
}
