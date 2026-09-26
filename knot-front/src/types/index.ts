/**
 * 前端共享类型
 *
 * 只放「跨模块复用」的形态约定；单个组件私有的类型就近写在组件里，不往这里堆。
 */

import type { Component } from "vue";

/** 后端列表接口统一返回结构：`{ list, total }` */
export interface PageResult<T = any> {
  list: T[];
  total: number;
}

/** 列表接口返回值：统一分页结构，或裸数组（无分页的场景） */
export type ListResponse<T = any> = PageResult<T> | T[];

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
