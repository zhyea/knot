import {get, post, postQuery} from "./http";
import type {Dict} from "@/types";

/**
 * 异步任务管理面接口（{@code /api/routing/async-tasks}）。
 *
 * <p>⭐ <b>读与写走的是两套后端链路</b>：{@code list}/{@code detail} 由管理端直接查库；
 * 三个写动作由管理端透传给网关（状态迁移与终态事件只能在网关发生）。
 * 因此写接口的失败可能是「网关不可用」，与「任务不存在」要分开提示——前者的处理人是运维，
 * 后者只需要刷新一下。</p>
 */

/** 分页检索：keyword / status / protocol / profileCode / submittedFrom / submittedTo */
export function listAsyncTasks(params: Dict) {
  return postQuery("/api/routing/async-tasks/list", params);
}

/** 任务详情（含四段 JSON 原文） */
export function getAsyncTask(taskCode: string) {
  return get(`/api/routing/async-tasks/${encodeURIComponent(taskCode)}`);
}

/** 终止在途任务（网关落 CANCELLED 并发终态事件） */
export function cancelAsyncTask(taskCode: string) {
  return post(`/api/routing/async-tasks/${encodeURIComponent(taskCode)}/cancel`);
}

/** 重试失败 / 超时任务（网关重开轮询，不改上游任务号） */
export function retryAsyncTask(taskCode: string) {
  return post(`/api/routing/async-tasks/${encodeURIComponent(taskCode)}/retry`);
}

/** 立即按保留窗口清理终态行，返回删除行数 */
export function cleanupAsyncTasks() {
  return post<number>("/api/routing/async-tasks/cleanup");
}
