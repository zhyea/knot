import {postQuery, post, put, get, del, postEventStream, postGatewayJson} from "./http";
import type {Dict} from "@/types";
import type {OptionItem, TestRequestPresetOptionMeta} from "@/types";
import type {OptionPage} from "./options";

export function listRoutingRules(params: Dict) {
  return postQuery("/api/routing-rules/list", params);
}

export function listRoutingConsumers(params: Dict) {
  return postQuery("/api/routing-consumers/list", params);
}

/** 消费者详情（含 secretKey）：路由测试按 id 精确取密钥，避免列表批量下发。 */
export function getRoutingConsumer(id: number | string) {
  return get<Dict>(`/api/routing-consumers/${id}`);
}

/** 消费者详情（含 secretKey）：路由测试按业务码取密钥，绑定收敛为 consumerCode 后使用。 */
export function getRoutingConsumerByCode(code: string) {
  return get<Dict>(`/api/routing-consumers/code/${code}`);
}

export function createRoutingConsumer(payload: Dict) {
  return post("/api/routing-consumers", payload);
}

export function updateRoutingConsumer(id: number | string, payload: Dict) {
  return put(`/api/routing-consumers/${id}`, payload);
}

export function updateRoutingConsumerStatus(id: number | string, enabled: boolean) {
  return put(`/api/routing-consumers/${id}/status`, {enabled});
}

export function rotateRoutingConsumerSecret(id: number | string) {
  return post(`/api/routing-consumers/${id}/rotate-secret`);
}

export function checkRoutingConsumerCode(code: string, excludeId: number | string | null) {
  return get("/api/routing-consumers/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

export function createRoutingRule(payload: Dict) {
  return post("/api/routing-rules", payload);
}

export function updateRoutingRule(id: number | string, payload: Dict) {
  return put(`/api/routing-rules/${id}`, payload);
}

export function updateRoutingRuleStatus(id: number | string, enabled: boolean) {
  return put(`/api/routing-rules/${id}/status`, {enabled});
}

export function checkRoutingRuleCode(code: string, excludeId: number | string | null) {
  return get("/api/routing-rules/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

/**
 * 路由规则调试（非流式）：直接请求网关协议地址。
 * 请求头由调试抽屉「Headers」表格组装后透传——Authorization / Rule / Content-Type
 * 等一律以表格为准（用户删除的行即不发送），此处不再内置默认头。
 */
export function testRoutingRule(
  gatewayUrl: string,
  payload: Dict,
  requestHeaders: Record<string, string>
) {
  return postGatewayJson(gatewayUrl, payload, requestHeaders);
}

/**
 * 流式路由规则测试：直接请求网关协议地址，返回句柄供增量解析 SSE 事件。
 * 管理端只负责配置/选择目标；网关需要消费者 API Key 和 Rule 头，不能走 /api 管理接口。
 * 请求头由调试抽屉「Headers」表格组装后透传（Authorization / Rule 等以表格为准）。
 * XHR 通道：响应头名保留服务端原始大小写（fetch 的 Headers 会统一小写）。
 */
export function testRoutingRuleStream(
  gatewayUrl: string,
  payload: Dict,
  requestHeaders: Record<string, string>,
  signal?: AbortSignal
) {
  return postEventStream(gatewayUrl, payload, signal, requestHeaders);
}

/**
 * 异步任务查询（轮询）：直接 POST 网关任务端点。
 *
 * <p>句柄在路径上、无请求体——回执里的 {@code query_url} 客户端可原样使用。
 * ⚠ 必须 POST：网关侧按 POST 注册，GET 会 405（见 `10-backend-java.md §5.2`）。</p>
 *
 * @param signal 关抽屉 / 发起新测试时中止在途查询，别让「已停止」只停留在文案上
 */
export function queryGatewayTask(
  url: string,
  requestHeaders: Record<string, string>,
  signal?: AbortSignal
) {
  return postGatewayJson(url, {}, requestHeaders, signal);
}

/** 调试协议能力：gatewayPath / hint / promptField（默认请求体不再硬编码，改由预设请求维护） */
export function listDebugCapabilities() {
  return get("/api/routing-rules/debug-capabilities");
}

/**
 * 规则各调试目标支持的接口协议：后端权威下发（与调试准入校验同源），
 * 前端不再自行解析模型/模型池的协议绑定。
 */
export function listRoutingRuleDebugProtocols(ruleId: number | string) {
  return get<{
    targets: Array<{ targetType: string; targetId: number | string; primary: boolean; protocols: string[] }>;
  }>(`/api/routing-rules/${ruleId}/debug-protocols`);
}

// ===================== 预设请求（路由调试请求用例） =====================

export function listTestRequestPresets(params: Dict) {
  return postQuery("/api/test-request-presets/list", params);
}

/**
 * 下拉候选：统一 OptionPage 契约（value=id，label=name）。
 * 接口协议已收敛到 meta：{@code meta.protocolCode}（顶层 code 随 options 强类型化移除）。
 */
export function listTestRequestPresetOptions() {
  return get<OptionPage<OptionItem<TestRequestPresetOptionMeta>>>("/api/test-request-presets/options");
}

/** 预设详情：选中后按 id 取完整请求体模板（options 不含 requestBody）。 */
export function getTestRequestPreset(id: number | string) {
  return get<Dict>(`/api/test-request-presets/${id}`);
}

export function createTestRequestPreset(payload: Dict) {
  return post("/api/test-request-presets", payload);
}

export function updateTestRequestPreset(id: number | string, payload: Dict) {
  return put(`/api/test-request-presets/${id}`, payload);
}

export function updateTestRequestPresetStatus(id: number | string, enabled: boolean) {
  return put(`/api/test-request-presets/${id}/status`, {enabled});
}

export function deleteTestRequestPreset(id: number | string) {
  return del(`/api/test-request-presets/${id}`);
}

// ===================== 路由测试·计费裁判（v7 三区双盲校验） =====================

/** 自检项（八项，代码精确，权威；三态 PASS/FAIL/SKIP） */
export type BillingCheckStatus = "PASS" | "FAIL" | "SKIP";
export type BillingCheckCode =
  | "USAGE_NOT_EMPTY"
  | "TOTAL_TOKENS_CONSISTENT"
  | "COST_FORMULA_MATCH"
  | "GATEWAY_RECOMPUTE_MATCH"
  | "RULE_MODE_MATCH"
  | "RULE_SOURCE_EXPECTED"
  | "UPSTREAM_COST_DEVIATION"
  | "ASYNC_SETTLEMENT_CONSISTENCY";

export interface BillingCheckResult {
  code: BillingCheckCode;
  status: BillingCheckStatus;
  /** FAIL/SKIP 的可读原因；PASS 为空 */
  message?: string | null;
}

/** 计费明细行（与网关 NormalizedUsageDetail 同构，金额为十进制普通字符串） */
export interface BillingFactAmountLine {
  type?: string | null;
  tokens?: number | null;
  cost?: string | null;
  /** 每百万单位单价 */
  price?: string | null;
}

/** 一份计费账（被告账 / 影子重算账同型） */
export interface BillingFactAmount {
  totalTokens?: number | null;
  totalCost?: string | null;
  currency?: string | null;
  details?: BillingFactAmountLine[] | null;
}

/** 计费规则快照（被告链依据） */
export interface BillingFactRuleRef {
  code?: string | null;
  billingMode?: string | null;
  unit?: string | null;
  currency?: string | null;
  pricingPlan?: string | null;
  /** 命中层级（EXPLICIT / FALLBACK / MISSING ...） */
  matchedBy?: string | null;
  versionCode?: string | null;
}

/** 单条 JEV 裁判答案（当前计费裁判四题统一使用 choice） */
export interface JevAnswerItem {
  key?: string | null;
  type?: string | null;
  value?: unknown;
  confidence?: number | null;
  legend?: string[] | null;
  status?: "ANSWERED" | "NOT_RETURNED" | string | null;
  note?: string | null;
}

/** JEV 计费裁判结论（available=false 时以 reason 表达降级） */
export interface RoutingBillingVerdict {
  available: boolean;
  reason?: string | null;
  summary?: string | null;
  answers?: JevAnswerItem[] | null;
  rawResponse?: string | null;
}

/** 端点聚合结果（三区一站式） */
export interface RoutingBillingVerdictResult {
  /** 被告用量：knot_extend.usage 原样 */
  producedUsage: Dict;
  /** 被告账：knot_extend.billing */
  producedBilling: BillingFactAmount | null;
  ruleRef: BillingFactRuleRef | null;
  /** 上游 usage 原文（证据） */
  upstreamUsage: Dict;
  upstreamCost: string | null;
  /** 影子重算账（仅计费事实区展示，不喂 JEV） */
  shadowBilling: BillingFactAmount | null;
  checks: BillingCheckResult[];
  /** 影子链内部异常摘要（null = 影子链正常完成） */
  shadowError: string | null;
  verdict: RoutingBillingVerdict;
}

export interface RoutingBillingVerdictRequestPayload {
  modelCode?: string | null;
  targetCode?: string | null;
  protocol?: string | null;
  stream?: boolean;
  /** 被告：knot_extend 整包（{usage, billing}） */
  knotExtend: Dict;
  /** 依据：上游原始 usage 原文 */
  upstreamUsage?: Dict;
  /** 依据：请求侧计费属性 */
  requestBody?: Dict;
  sessionId?: string | null;
  /**
   * 依据：上游**响应体原文**。
   *
   * <p>⭐ 异步视频必须有它：视频提取器从响应里取输出时长与产物数，只给 {@code upstreamUsage}
   * 那一段 usage 算不出分档。同步链路不必传（后端会退回 {@code {usage}} 口径）。</p>
   */
  upstreamBody?: Dict | null;
  /** 异步任务终态词（{@code completed} / {@code failed} / 在途词）；同步链路为空 */
  taskStatus?: string | null;
}

/**
 * 路由规则测试·计费裁判：测试响应到手后回传当次产出，做「被告账 + 影子重算 + 八项自检 + JEV 双盲」。
 * 与 /test 同权限（routing:rule:test）；不阻塞主流程，失败降级由后端以 available=false 表达。
 */
export function testRoutingRuleBillingVerdict(
  ruleId: number | string,
  payload: RoutingBillingVerdictRequestPayload
) {
  // 判定含一次网关转发与 LLM 调用，放宽超时（不阻塞主流程，慢一点可接受）；
  // silentError：失败态由抽屉内「计费校验请求失败」区域自行展示，不再弹全局消息重复报警
  return post<RoutingBillingVerdictResult>(
    `/api/routing-rules/${ruleId}/test/billing-verdict`,
    payload,
    {timeout: 60000, silentError: true}
  );
}
