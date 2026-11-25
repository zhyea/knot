import {postQuery, post, put, get} from "./http";
import type {Dict, PageResult} from "@/types";

export function listUsers(params: Dict) {
  return postQuery<PageResult<Dict>>("/api/users", params);
}

/** 用户详情：编辑抽屉打开时按 id 精确取，不复用列表行数据。 */
export function getUser(id: number | string) {
  return get<Dict>(`/api/users/${id}`);
}

export function createUser(payload: Dict) {
  return post<Dict>("/api/users/create", payload);
}

export function updateUserStatus(id: number | string, payload: Dict) {
  return put<Dict>(`/api/users/${id}/status`, payload);
}

export function updateUser(id: number | string, payload: Dict) {
  return put<Dict>(`/api/users/${id}`, payload);
}

export interface ResetPasswordResult {
  id: number;
  username: string;
  oneTimePassword: string;
}

export function resetUserPassword(id: number | string) {
  return put<ResetPasswordResult>(`/api/users/${id}/reset-password`);
}
