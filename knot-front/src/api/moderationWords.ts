import {del, get, post, postQuery, put} from "./http";
import type {Dict} from "@/types";

/**
 * 敏感词库（内容安全）：/api/moderation-words。
 *
 * 词条是 Aho-Corasick 自动机的数据源，由网关同步插件在请求线程内联消费；
 * 本模块只做管理面 CRUD + 导入导出，不参与匹配链路。
 */
export function listModerationWords(params: Dict) {
  return postQuery("/api/moderation-words/list", params);
}

export function getModerationWord(id: number | string) {
  return get(`/api/moderation-words/${id}`);
}

/**
 * 唯一键是 (word, category_code)，故可用性判断必须同时带分类。
 */
export function checkModerationWord(word: string, categoryCode: string, excludeId: number | string | null) {
  return get("/api/moderation-words/check-code", {
    params: {word, categoryCode, excludeId: excludeId ?? undefined}
  });
}

export function createModerationWord(payload: Dict) {
  return post("/api/moderation-words", payload);
}

export function updateModerationWord(id: number | string, payload: Dict) {
  return put(`/api/moderation-words/${id}`, payload);
}

export function updateModerationWordStatus(id: number | string, enabled: boolean) {
  return put(`/api/moderation-words/${id}/status`, {enabled});
}

export function deleteModerationWord(id: number | string) {
  return del(`/api/moderation-words/${id}`);
}

/** 恢复已逻辑删除的词条：唯一键按物理行判定，删除后同词只能恢复不能新建。 */
export function restoreModerationWord(id: number | string) {
  return put(`/api/moderation-words/${id}/restore`);
}

/** 批量导入：逐行独立校验，部分失败不影响已成功的行。 */
export function importModerationWords(rows: Dict[]) {
  return post("/api/moderation-words/import", rows);
}

/** 导出全部未删除词条（含停用），前端转 CSV。 */
export function exportModerationWords() {
  return get("/api/moderation-words/export");
}
