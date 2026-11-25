import {postQuery, post, put, del, get} from "./http";
import type {Dict} from "@/types";

export function listApps(params: Dict) {
  return postQuery("/api/apps/list", params);
}

/** 应用详情：编辑抽屉打开时按 id 精确取，不复用列表行数据。 */
export function getApp(id: number | string) {
  return get<Dict>(`/api/apps/${id}`);
}

export function createApp(payload: Dict) {
  return post("/api/apps", payload);
}

export function updateApp(id: number | string, payload: Dict) {
  return put(`/api/apps/${id}`, payload);
}

export function deleteApp(id: number | string) {
  return del(`/api/apps/${id}`);
}

