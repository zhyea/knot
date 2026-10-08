import {postQuery} from "./http";
import type {
  Dict,
  OptionItem,
  OptionMeta,
  ProviderAccountMeta,
  LogicalModelMeta,
  ModelMeta,
  UserOptionMeta,
  DepartmentOptionMeta,
  AppOptionMeta,
  RoutingConsumerOptionMeta,
  RoleOptionMeta,
  TestRequestPresetOptionMeta
} from "@/types";

/**
 * 下拉候选项（与后端 `OptionItem<M>` 对齐；value 类型随资源，勿强制转换）。
 *
 * 泛型 M：meta 的强类型（见 `@/types` 的各资源 meta interface）。
 * 不显式指定时默认 `OptionItem<OptionMeta>`。
 *
 * 顶层 `code` 已移除：业务码属资源语义，一律走资源专属 `meta`（如 `meta.consumerCode` /
 * `meta.appCode` / `meta.protocolCode`），绑定键恒为 `value`。
 * 枚举的 `EnumOptionItem.code` 是枚举项的正式取值，不受此约束。
 */
export type {
  OptionItem,
  OptionMeta,
  ProviderAccountMeta,
  LogicalModelMeta,
  ModelMeta,
  UserOptionMeta,
  DepartmentOptionMeta,
  AppOptionMeta,
  RoutingConsumerOptionMeta,
  RoleOptionMeta,
  TestRequestPresetOptionMeta
} from "@/types";

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
// id 型：用户/部门/应用/路由消费者/角色；code 型：供应商账户/计费规则/模型池/供应商信息；
// modelCode 型：统一模型/供应商模型。
// 业务码不再占顶层 code —— id 型资源的业务码走各自 meta
// （username / deptCode / appCode / consumerCode / roleCode）。

export function listUserOptions(params: OptionQuery) {
  return options<OptionItem<UserOptionMeta>>("/api/users/options", params);
}

export function listDepartmentOptions(params: OptionQuery) {
  return options<OptionItem<DepartmentOptionMeta>>("/api/system/departments/options", params);
}

export function listAppOptions(params: OptionQuery) {
  return options<OptionItem<AppOptionMeta>>("/api/apps/options", params);
}

export function listProviderAccountOptions(params: OptionQuery) {
  return options<OptionItem<ProviderAccountMeta>>("/api/provider-accounts/options", params);
}

export function listLogicalModelOptions(params: OptionQuery) {
  return options<OptionItem<LogicalModelMeta>>("/api/logical-models/options", params);
}

export function listModelOptions(params: OptionQuery) {
  return options<OptionItem<ModelMeta>>("/api/models/options", params);
}

export function listModelPoolOptions(params: OptionQuery) {
  return options("/api/model-pools/options", params);
}

export function listRoutingConsumerOptions(params: OptionQuery) {
  return options<OptionItem<RoutingConsumerOptionMeta>>("/api/routing-consumers/options", params);
}

export function listBillingRuleOptions(params: OptionQuery) {
  return options("/api/billing/options", params);
}

// 阶段四扩范围（原为列表接口例外）：角色 value=id；供应商信息 value=code。

export function listRoleOptions(params: OptionQuery) {
  return options<OptionItem<RoleOptionMeta>>("/api/system/authorizations/roles/options", params);
}

export function listProviderProfileOptions(params: OptionQuery) {
  return options("/api/provider-profiles/options", params);
}
