import {del, get, post, put} from "@/api/http";
import type {Dict} from "@/types";

export function listAuthorizationMenus(params: Dict) {
  return get<Dict[]>("/api/system/authorizations/menus", { params });
}

/** 菜单详情：编辑弹窗打开时按 id 精确取，不复用列表行数据。 */
export function getAuthorizationMenu(id: number | string) {
  return get<Dict>(`/api/system/authorizations/menus/${id}`);
}

export function createAuthorizationMenu(data: Dict) {
  return post<Dict>("/api/system/authorizations/menus", data);
}

export function updateAuthorizationMenu(id: number | string, data: Dict) {
  return put<Dict>(`/api/system/authorizations/menus/${id}`, data);
}

export function updateAuthorizationMenuStatus(id: number | string, enabled: boolean) {
  return put<Dict>(`/api/system/authorizations/menus/${id}/status`, { enabled });
}

export function deleteAuthorizationMenu(id: number | string) {
  return del<Dict>(`/api/system/authorizations/menus/${id}`);
}
