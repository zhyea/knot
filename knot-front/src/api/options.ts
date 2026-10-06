import {postQuery} from "./http";
import type {Dict} from "@/types";

/** 下拉候选项（与后端 OptionItem 对齐；value 类型随资源，勿强制转换）。 */
export interface OptionItem {
  value: string | number;
  label: string;
  code?: string | null;
  disabled?: boolean | null;
  meta?: Record<string, unknown> | null;
}

/** 下拉候选分页（与后端 OptionPage 对齐）。missingValues = 已选但不存在/无权限。 */
export interface OptionPage<T = OptionItem> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  missingValues: string[];
}

/** 公共查询字段（与后端 OptionQuery 对齐）。 */
export interface OptionQuery extends Dict {
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
  /** 已选值回显（上限 100） */
  values?: (string | number)[];
  /** 仅启用项，默认 true */
  enabledOnly?: boolean;
  includeDeleted?: boolean;
}

function options<T = OptionItem>(url: string, params: OptionQuery): Promise<OptionPage<T>> {
  return postQuery<OptionPage<T>>(url, params);
}

// value 语义（冻结，勿猜测）：
// id 型：用户/部门/应用/路由消费者；code 型：供应商账户/计费规则/模型池；modelCode 型：统一模型/供应商模型。

export function listUserOptions(params: OptionQuery) {
  return options("/api/users/options", params);
}

export function listDepartmentOptions(params: OptionQuery) {
  return options("/api/system/departments/options", params);
}

export function listAppOptions(params: OptionQuery) {
  return options("/api/apps/options", params);
}

export function listProviderAccountOptions(params: OptionQuery) {
  return options("/api/provider-accounts/options", params);
}

export function listLogicalModelOptions(params: OptionQuery) {
  return options("/api/logical-models/options", params);
}

export function listModelOptions(params: OptionQuery) {
  return options("/api/models/options", params);
}

export function listModelPoolOptions(params: OptionQuery) {
  return options("/api/model-pools/options", params);
}

export function listRoutingConsumerOptions(params: OptionQuery) {
  return options("/api/routing-consumers/options", params);
}

export function listBillingRuleOptions(params: OptionQuery) {
  return options("/api/billing/options", params);
}

// 阶段四扩范围（原为列表接口例外）：角色 value=id；供应商信息 value=code。

export function listRoleOptions(params: OptionQuery) {
  return options("/api/system/authorizations/roles/options", params);
}

export function listProviderProfileOptions(params: OptionQuery) {
  return options("/api/provider-profiles/options", params);
}
