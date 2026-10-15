import {get, post} from "./http";

export interface GatewayOutageItem {
  ruleCode: string;
  modelCode: string;
  failures: number;
  slows: number;
  windowEndAt: number;
  ejectedUntil: number;
  probeUntil: number;
  cooldownMs: number;
  reason: string;
}

export interface GatewayOutageAck {
  ruleCode: string;
  modelCode: string;
  action: string;
  ejected: boolean;
  ejectedUntil: number;
  reason: string;
}

/** 查询当前摘除目标（摘除中 + 探活中），可按规则 / 模型过滤。 */
export function listGatewayOutages(params: { ruleCode?: string; modelCode?: string }) {
  return get<GatewayOutageItem[]>("/api/gateway/outages", {params});
}

/** 手动恢复：清除「规则 × 模型」的摘除状态。 */
export function releaseGatewayOutage(payload: { ruleCode: string; modelCode: string }) {
  return post<GatewayOutageAck>("/api/gateway/outages/release", payload);
}

/** 手动摘除：不依赖窗口计数，直接把「规则 × 模型」打入冷却。 */
export function ejectGatewayOutage(payload: {
  ruleCode: string;
  modelCode: string;
  cooldownSec?: number;
  reason?: string;
}) {
  return post<GatewayOutageAck>("/api/gateway/outages/eject", payload);
}
