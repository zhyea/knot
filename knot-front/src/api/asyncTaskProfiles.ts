import {get, post, postQuery} from "./http";
import type {Dict} from "@/types";

/**
 * 异步任务档案（任务协议）管理面接口（`/api/system/async-task-profiles`）。
 *
 * <p>⭐ <b>为什么挂在 {@code /api/system/} 而不是 {@code /api/routing/}</b>：档案是系统级配置
 * （改错会把所有在途任务带偏），任务实例是运行数据。两者权限分开登记。</p>
 *
 * <p>⚠ <b>试跑的 {@code errors} 不是请求失败</b>：HTTP 仍是 200，那是「配置有问题，请看这里」。
 * 前端不要用 catch 处理它，否则每次表达式写错都会弹一次「服务器错误」。</p>
 */

/** 分页检索：keyword / protocol / status */
export function listAsyncTaskProfiles(params: Dict) {
  return postQuery("/api/system/async-task-profiles/list", params);
}

/** 档案详情（含被引用行数） */
export function getAsyncTaskProfile(profileCode: string) {
  return get(`/api/system/async-task-profiles/${encodeURIComponent(profileCode)}`);
}

/** 新建或更新（按编码判断；已绑定时编码与协议不可变） */
export function saveAsyncTaskProfile(payload: Dict) {
  return post("/api/system/async-task-profiles/save", payload);
}

/** 删除（仍被模型引用时后端拒绝） */
export function deleteAsyncTaskProfile(profileCode: string) {
  return post(`/api/system/async-task-profiles/delete/${encodeURIComponent(profileCode)}`);
}

/** 任务协议下拉候选项（模型表单用；只含启用档案，disabled 由后端算） */
export function listAsyncTaskProfileOptions() {
  return post("/api/system/async-task-profiles/options");
}

/** 试跑：贴创建/查询响应，按当前表达式求值（未保存也可以跑） */
export function dryRunAsyncTaskProfile(payload: Dict) {
  return post("/api/system/async-task-profiles/dry-run", payload);
}
