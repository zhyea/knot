import {computed, ref, type Ref} from "vue";
import {findObjectField, findSiblingField, parseStreamJson} from "@/utils/routingTestPayload";
import type {Dict} from "@/types";
import type {
  RoutingBillingVerdictRequestPayload,
  RoutingBillingVerdictResult
} from "@/api/routing";

/** 响应体里的被告负载字段名（网关注入） */
const KNOT_EXTEND_FIELD = "knot_extend";
/** 与 knot_extend 并列的上游原始 usage 字段名 */
const RAW_USAGE_FIELD = "usage";
/** 承接模型响应头（小写归一后的键）；大写形式作为兼容回退 */
const MODEL_RESPONSE_HEADER = "x-knot-model";

/**
 * 计费裁判的上下文：抽屉把「当前选择」与「裁判接口」注入进来。
 *
 * <p>接口走注入而非直接 import，是为了让本 composable 可以脱离 HTTP 单测——
 * 裁判是双盲校验的结论区，串台（过期结果覆盖新结果）与降级分支必须能被钉住。</p>
 */
export interface BillingVerdictContext {
  /** 当前规则 id；为 null 时一律不发起裁判（测试抽屉打开时规则必然已存，防御空值） */
  ruleId: () => number | null | undefined;
  /** 当前选中目标的业务码 */
  targetCode: () => string | null | undefined;
  /** 当前协议 code */
  protocol: () => string;
  /** 当次响应头（键已小写归一），用于取承接模型 */
  responseHeaders: Ref<Record<string, string>>;
  /** 发起裁判请求；只在注入侧实现，便于测试替换 */
  requestVerdict: (
    ruleId: number,
    payload: RoutingBillingVerdictRequestPayload
  ) => Promise<RoutingBillingVerdictResult>;
}

/**
 * 计费裁判（JEV 双盲校验 v7 §3.2 第二步）的状态与动作。
 *
 * <p>纪律：裁判<b>不发第二次上游请求</b>——被告就是抽屉里已展示过的那次调用的产出。
 * 无 knot_extend 即短路（skipped），请求失败降级为 error，两者都不影响已渲染的测试结果。</p>
 */
export function useBillingVerdict(ctx: BillingVerdictContext) {
  /** 当次响应提取的 knot_extend 被告负载 {usage, billing}；null = 本次响应无消费详情 */
  const knotExtend = ref<Dict | null>(null);
  /** 与 knot_extend 并列的上游原始 usage 原文（裁判证据） */
  const upstreamUsage = ref<Dict | null>(null);
  const verdictResult = ref<RoutingBillingVerdictResult | null>(null);
  const verdictLoading = ref(false);
  /** 本次响应无 knot_extend：链路短路，未发起裁判（v7 §3.1 降级提示） */
  const verdictSkipped = ref(false);
  const verdictError = ref("");
  /** 裁判轮次令牌：新测试发起即自增，过期结果丢弃，避免连续测试结果串台 */
  let verdictToken = 0;

  /** 重置裁判区：新测试 / 抽屉开关时调用，令牌自增使在途请求的结果作废 */
  function resetVerdictState(): void {
    verdictToken += 1;
    knotExtend.value = null;
    upstreamUsage.value = null;
    verdictResult.value = null;
    verdictLoading.value = false;
    verdictSkipped.value = false;
    verdictError.value = "";
  }

  /**
   * 提取 knot_extend 被告负载与并列的上游原始 usage（v7 §4.1）：
   * 整包响应 = 响应体顶层字段；流式 = [DONE] 前网关注入的独立 SSE 事件（同构 JSON）。
   * 无 knot_extend 字段时静默忽略（消费者未开返回消费详情且未勾选临时开启）。
   */
  function captureKnotExtend(payload: unknown): void {
    const extend = findObjectField(payload, KNOT_EXTEND_FIELD);
    if (!extend || typeof extend !== "object" || Array.isArray(extend)) {
      return;
    }
    knotExtend.value = extend as Dict;
    const raw = findSiblingField(payload, KNOT_EXTEND_FIELD, RAW_USAGE_FIELD);
    upstreamUsage.value = raw && typeof raw === "object" ? (raw as Dict) : null;
  }

  /** 网关插入的 knot_extend 行可能与紧随其后的 [DONE] 共处一个 SSE 事件。 */
  function captureKnotExtendFromStreamData(data: string): void {
    const parsed = parseStreamJson(data);
    if (!parsed.error && parsed.value && typeof parsed.value === "object") {
      captureKnotExtend(parsed.value);
      if (knotExtend.value) {
        return;
      }
    }
    for (const line of data.split(/\r?\n/)) {
      const lineParsed = parseStreamJson(line);
      if (!lineParsed.error && lineParsed.value && typeof lineParsed.value === "object") {
        captureKnotExtend(lineParsed.value);
        if (knotExtend.value) {
          return;
        }
      }
    }
  }

  /**
   * 实际承接模型：优先响应头 X-Knot-Model，缺失回退当前所选目标。
   * 「全部目标」下网关按规则实际路由，命中模型只能从响应头得知。
   */
  function servingModelCode(): string | null {
    const headers = ctx.responseHeaders.value;
    const header = headers[MODEL_RESPONSE_HEADER] || headers["X-Knot-Model"];
    const code = header?.trim();
    return code || ctx.targetCode() || null;
  }

  /**
   * 计费裁判第二步：测试响应到手后自动发起，不阻塞主流程。
   *
   * @param requestBody 本次发给网关的请求体（裁判据此重算用量）
   * @param stream      本次是否为流式调用（影响提取器口径）
   * @param evidence    异步链路的补充证据：终态响应里的上游原文与终态词。
   *                    ⭐ 视频的用量在<b>响应</b>里（输出时长 / 产物数），只给 requestBody 与
   *                    upstreamUsage 是算不出分档的；终态词则是「成功必有账」那条自检的唯一依据
   */
  async function triggerBillingVerdict(
    requestBody: Dict,
    stream: boolean,
    evidence?: { upstreamBody?: Dict | null; taskStatus?: string | null }
  ): Promise<void> {
    const token = verdictToken;
    const ruleId = ctx.ruleId();
    if (!ruleId) {
      return;
    }
    if (!knotExtend.value) {
      verdictSkipped.value = true;
      return;
    }
    verdictLoading.value = true;
    verdictError.value = "";
    try {
      const payload: RoutingBillingVerdictRequestPayload = {
        modelCode: ctx.targetCode() ?? null,
        targetCode: servingModelCode(),
        protocol: ctx.protocol() || null,
        stream,
        knotExtend: knotExtend.value,
        upstreamUsage: upstreamUsage.value ?? {},
        requestBody,
        sessionId: null,
        upstreamBody: evidence?.upstreamBody ?? null,
        taskStatus: evidence?.taskStatus ?? null
      };
      const result = await ctx.requestVerdict(ruleId, payload);
      // 令牌已变 = 期间又发起了新测试，本次结果作废
      if (token !== verdictToken) {
        return;
      }
      verdictResult.value = result;
    } catch (error) {
      if (token !== verdictToken) {
        return;
      }
      const err = error as Dict;
      verdictError.value = String(err?.response?.data?.message || err?.message || "计费校验请求失败");
    } finally {
      if (token === verdictToken) {
        verdictLoading.value = false;
      }
    }
  }

  /** 裁判区是否有内容可展示（跳过与失败也是设计内的展示态） */
  const verdictActive = computed(
    () =>
      verdictLoading.value ||
      verdictSkipped.value ||
      verdictError.value !== "" ||
      verdictResult.value !== null
  );

  return {
    knotExtend,
    upstreamUsage,
    verdictResult,
    verdictLoading,
    verdictSkipped,
    verdictError,
    verdictActive,
    resetVerdictState,
    captureKnotExtend,
    captureKnotExtendFromStreamData,
    servingModelCode,
    triggerBillingVerdict
  };
}
