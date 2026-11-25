import {del, get, post, put} from "@/api/http";
import type {Dict} from "@/types";

export function listAuthorizationModules(params: Dict) {
  return get<Dict[]>("/api/system/authorizations/modules", { params });
}

/** 模块详情：编辑弹窗打开时按 id 精确取，不复用列表行数据。 */
export function getAuthorizationModule(id: number | string) {
  return get<Dict>(`/api/system/authorizations/modules/${id}`);
}

export function createAuthorizationModule(data: Dict) {
  return post<Dict>("/api/system/authorizations/modules", data);
}

export function updateAuthorizationModule(id: number | string, data: Dict) {
  return put<Dict>(`/api/system/authorizations/modules/${id}`, data);
}

export function updateAuthorizationModuleStatus(id: number | string, enabled: boolean) {
  return put<Dict>(`/api/system/authorizations/modules/${id}/status`, { enabled });
}

export function deleteAuthorizationModule(id: number | string) {
  return del<Dict>(`/api/system/authorizations/modules/${id}`);
}
