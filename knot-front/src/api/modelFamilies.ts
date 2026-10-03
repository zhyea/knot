import {del, get, post, postQuery, put} from "./http";
import type {Dict} from "@/types";

/**
 * 模型族：模型域的独立维护入口，数据仍存枚举表 ks_enum_configs（category='model_family'），
 * 与「系统管理 / 枚举管理」同源，只是权限独立（model:model-family:*）。
 */
export function listModelFamilies(params: Dict) {
  return postQuery("/api/model-families/list", params);
}

export function getModelFamily(id: number | string) {
  return get(`/api/model-families/${id}`);
}

export function checkModelFamilyCode(code: string, excludeId: number | string | null) {
  return get("/api/model-families/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

export function createModelFamily(payload: Dict) {
  return post("/api/model-families", payload);
}

export function updateModelFamily(id: number | string, payload: Dict) {
  return put(`/api/model-families/${id}`, payload);
}

export function updateModelFamilyStatus(id: number | string, enabled: boolean) {
  return put(`/api/model-families/${id}/status`, {enabled});
}

export function deleteModelFamily(id: number | string) {
  return del(`/api/model-families/${id}`);
}
