/**
 * 前端共享类型
 *
 * 只放「跨模块复用」的形态约定；单个组件私有的类型就近写在组件里，不往这里堆。
 */

import type {Component} from "vue";

/** 后端列表接口统一返回结构：`{ list, total }` */
export interface PageResult<T = any> {
  list: T[];
  total: number;
}

/** 列表接口返回值：统一分页结构，或裸数组（无分页的场景） */
export type ListResponse<T = any> = PageResult<T> | T[];

/**
 * 独立 options 接口（下拉候选）统一契约。
 * 硬约束见 `.workbuddy/memory/options-refactor-constraints.md` 第 0/1 节：
 * - 有传参一律 POST /api/{resource}/options；仅无传参才 GET
 * - `value` 类型随资源（id 型=number，code 型=string），前端不得自行猜测
 * - `missingValues` 中的项必须统一展示「已选项不存在或无权限」并阻止非法提交
 */
export interface OptionQuery {
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
  /** 已选值回显（上限 100），编辑场景不在第一页的项也须返回 */
  values?: string[];
  /** 仅返回启用项，默认 true */
  enabledOnly?: boolean;
  /** 含逻辑删除项，默认 false */
  includeDeleted?: boolean;
  /** 资源白名单过滤，仅含后端显式声明的字段，禁止任意透传 */
  filters?: Record<string, unknown>;
}

/**
 * 下拉候选项 meta 强类型（与后端 `vo.common.meta.*` 各 record 对齐）。
 * 仅承载下拉确实需要、且非敏感的派生信息；字段集合由后端 `OptionsMapper.xml` 的
 * `json_object` 生成，前端不得假设额外键。
 */
/** 供应商账户：上游网关 baseUrl（非凭据）。 */
export interface ProviderAccountMeta {
  baseUrl: string;
}

/** 统一模型：协议联动（modelType）与计费族过滤（modelFamily）、启用态。 */
export interface LogicalModelMeta {
  modelType?: string | null;
  modelFamily?: string | null;
  status?: number | null;
}

/** 供应商模型：供应商 / 统一模型派生字段，供池内表格与表单联动展示。 */
export interface ModelMeta {
  providerName?: string | null;
  providerAccountCode?: string | null;
  modelName?: string | null;
  name?: string | null;
  modelType?: string | null;
  logicalModelCode?: string | null;
  status?: number | null;
}

/** 用户：登录名（value 是用户 id）。 */
export interface UserOptionMeta {
  username?: string | null;
}

/** 部门：部门编码（value 是部门 id）。 */
export interface DepartmentOptionMeta {
  deptCode?: string | null;
}

/** 应用：业务码（value 是应用 id）；应用凭据不进入 meta。 */
export interface AppOptionMeta {
  appCode?: string | null;
}

/** 路由消费者：消费码（value 是消费者 id）；secretKey 不进入 meta。 */
export interface RoutingConsumerOptionMeta {
  consumerCode?: string | null;
}

/** 角色：角色编码（value 是角色 id）。 */
export interface RoleOptionMeta {
  roleCode?: string | null;
}

/** 测试请求预设：接口协议（value 是预设 id），决定调试面板的协议适配器。 */
export interface TestRequestPresetOptionMeta {
  protocolCode?: string | null;
}

/** 所有带 meta 的下拉候选项类型联合（无 meta 资源 meta 恒为 null）。 */
export type OptionMeta =
  | ProviderAccountMeta
  | LogicalModelMeta
  | ModelMeta
  | UserOptionMeta
  | DepartmentOptionMeta
  | AppOptionMeta
  | RoutingConsumerOptionMeta
  | RoleOptionMeta
  | TestRequestPresetOptionMeta
  | null;

/**
 * 下拉候选项（与后端 `OptionItem<M>` 对齐）。
 * 泛型 M：meta 的强类型（默认 `OptionMeta` 联合，含 null）。value 类型随资源
 * （id 型=number，code 型=string），前端不得自行猜测，统一以 `string | number` 承接。
 *
 * 顶层 `code` 已移除：业务码属资源语义，一律走资源专属 `meta`（如 `meta.username` /
 * `meta.appCode` / `meta.consumerCode`），避免按资源猜测同名顶层字段的含义。
 * 枚举的 `EnumOptionItem.code` 是枚举项的正式取值，不受此约束，保持现状。
 * `meta` 不再是松散的 `Record<string, any>`，其结构由 M 约束。
 */
export interface OptionItem<M = OptionMeta> {
  value: string | number;
  label: string;
  disabled?: boolean | null;
  /** 仅承载下拉确实需要的非敏感业务信息，强类型由 M 约束 */
  meta?: M | null;
}

export interface OptionPage<T = OptionItem<OptionMeta>> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  /** 请求 values 中不存在 / 无权限的项 */
  missingValues: string[];
}

/** 任意键值对：后端 VO / 表单对象在未建模前的通用形态 */
export type Dict = Record<string, any>;

/** 下拉 / 单选 / 多选选项 */
export interface SelectOption<T = any> {
  label: string;
  value: T;
  disabled?: boolean;
}

/** 列表行：后端行对象的通用形态 */
export type Row = Dict;

declare module "vue-router" {
  interface RouteMeta {
    /** i18n key，优先于 title */
    titleKey?: string;
    /** 直接写死的标题（titleKey 缺失时的兜底） */
    title?: string;
  }
}

/** 请求配置里业务自定义的扩展字段由 `api/http.ts` 的模块增强提供 */
export type QueryFn<T = any> = (params: Dict) => Promise<ListResponse<T>>;

/** 树选择 / 下拉的通用选项（value 同时用作 :key，故限定为标量） */
export interface SelectOptionLike {
  label: string;
  value: string | number;
  children?: SelectOptionLike[];
}

/**
 * 资源对话框的字段描述（驱动 `AuthorizationEntityFormDialog` 渲染）
 *
 * `type` 决定渲染控件：textarea / number / switch / select / tree-select，缺省为普通 input。
 */
export interface ResourceField {
  key: string;
  label: string;
  required?: boolean;
  type?: "textarea" | "number" | "switch" | "select" | "tree-select" | string;
  placeholder?: string;
  defaultValue?: unknown;
  options?: SelectOptionLike[];
  rows?: number;
  maxlength?: number;
  min?: number;
  max?: number;
  /** el-switch 的开关取值，限定为标量 */
  activeValue?: string | number | boolean;
  inactiveValue?: string | number | boolean;
  nodeKey?: string;
  checkStrictly?: boolean;
  props?: Dict;
  /** 筛选项的定宽，如 "220px" */
  width?: string;
}

/** 表格列描述 */
export interface TableColumn {
  prop?: string;
  label?: string;
  width?: number | string;
  minWidth?: number | string;
  align?: "left" | "center" | "right" | string;
  /** 命中该列时启用的插槽名 */
  slot?: string;
  showOverflowTooltip?: boolean;
}

/** Element Plus 按钮 type 取值 */
export type ButtonType =
  | ""
  | "default"
  | "text"
  | "primary"
  | "success"
  | "warning"
  | "info"
  | "danger";

/** 行操作按钮描述（RowActions 渲染） */
export interface RowAction {
  key: string;
  label: string;
  type?: ButtonType;
  icon?: Component | string;
  disabled?: boolean;
  /** 真值即隐藏该操作 */
  hidden?: unknown;
  /** 字符串 = 确认文案；true = 用默认文案；假值 = 不确认 */
  confirm?: boolean | string;
}

/** 资源对话框提交器：接收表单 payload，返回请求 Promise */
export type ResourceSubmitter = (payload: Dict) => Promise<unknown>;
