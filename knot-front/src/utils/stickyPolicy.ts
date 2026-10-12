/**
 * 池内粘性路由策略：默认值与规范化
 * （Mirror 后端 StickyPolicy / org.chobit.knot.gateway.model.StickyPolicy）
 *
 * 模型池的 RANDOM / WEIGHTED 选择策略每请求重掷，同一会话的多轮请求会漂移到池内不同
 * 供应商账户/模型，导致上游 prompt cache（按账户 + 区域 + 部署命中）全部落空。
 * 开启粘性后，网关对请求头携带的粘性键做一致性 hash，把命中模型置顶为池内首选，
 * 其余候选仍按选择策略原序作为 failover 链——只改首选，不改故障转移顺序。
 *
 * 默认关闭：粘性是流量塑形（用均衡换缓存命中），必须显式 opt-in。
 * 实现为无状态一致性 hash，不记录绑定、无 TTL，因此这里没有任何过期类字段。
 */

import type {Dict} from "@/types";

export interface StickyPolicy {
  /** 关闭时池选择完全按 selection_strategy */
  enabled: boolean;
  /** 取粘性键的请求头名称 */
  headerName: string;
}

export const STICKY_HEADER_NAME_DEFAULT = "X-Knot-Sticky-Key";
/** 与后端 HEADER_NAME_LIMIT 一致，超长回落默认头名 */
export const STICKY_HEADER_NAME_LIMIT = 128;

export function defaultStickyPolicy(): StickyPolicy {
  return {
    enabled: false,
    headerName: STICKY_HEADER_NAME_DEFAULT
  };
}

/**
 * 规范化：非法或缺失字段退回默认，保证表单与提交载荷都是有效值（与后端紧凑构造器同口径）。
 */
export function normalizeStickyPolicy(raw: unknown): StickyPolicy {
  const empty = defaultStickyPolicy();
  if (!raw || typeof raw !== "object") {
    return empty;
  }
  const source = raw as Dict;
  return {
    enabled: source.enabled === true,
    headerName: normalizeHeaderName(source.headerName)
  };
}

function normalizeHeaderName(raw: unknown): string {
  const value = String(raw ?? "").trim();
  if (!value || value.length > STICKY_HEADER_NAME_LIMIT) {
    return STICKY_HEADER_NAME_DEFAULT;
  }
  return value;
}
