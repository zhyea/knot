import {describe, it, expect, vi} from "vitest";
import {ref} from "vue";
import {useBillingVerdict, type BillingVerdictContext} from "@/composables/useBillingVerdict";
import type {RoutingBillingVerdictResult} from "@/api/routing";
import type {Dict} from "@/types";

/**
 * 计费裁判是「双盲校验」的结论区，两条纪律必须钉死：
 * ① 轮次令牌——连续测试时过期结果不得覆盖新结果（串台）；
 * ② 降级——无被告短路为 skipped、请求失败降级为 error，都不许抛出去影响已渲染的测试结果。
 */

function makeCtx(overrides: Partial<BillingVerdictContext> = {}) {
  const calls: Array<{ruleId: number; payload: Dict}> = [];
  const base: BillingVerdictContext = {
    ruleId: () => 7,
    targetCode: () => "gpt-4o",
    protocol: () => "CHAT_COMPLETIONS",
    responseHeaders: ref<Record<string, string>>({}),
    requestVerdict: async (ruleId, payload) => {
      calls.push({ruleId, payload: payload as unknown as Dict});
      return {passed: true} as unknown as RoutingBillingVerdictResult;
    }
  };
  return {ctx: {...base, ...overrides}, calls};
}

describe("useBillingVerdict 前置条件与短路", () => {
  it("无 ruleId 时一律不发起裁判", async () => {
    const {ctx, calls} = makeCtx({ruleId: () => null});
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {billing: {}}});
    await v.triggerBillingVerdict({}, false);
    expect(calls).toHaveLength(0);
    expect(v.verdictSkipped.value).toBe(false);
  });

  it("无 knot_extend → skipped 短路，不发请求", async () => {
    const {ctx, calls} = makeCtx();
    const v = useBillingVerdict(ctx);
    await v.triggerBillingVerdict({}, false);
    expect(v.verdictSkipped.value).toBe(true);
    expect(calls).toHaveLength(0);
    expect(v.verdictResult.value).toBeNull();
  });

  it("被告存在时发起并把结果写入", async () => {
    const {ctx, calls} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {billing: {totalCost: "1"}}});
    await v.triggerBillingVerdict({model: "x"}, true);
    expect(calls).toHaveLength(1);
    expect(calls[0].ruleId).toBe(7);
    expect(calls[0].payload.stream).toBe(true);
    expect(v.verdictResult.value).toEqual({passed: true});
    expect(v.verdictLoading.value).toBe(false);
    expect(v.verdictSkipped.value).toBe(false);
  });

  it("异步证据随请求回传：upstreamBody 与 taskStatus 都进 payload", async () => {
    const {ctx, calls} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {billing: {totalCost: "0.5"}}});
    const upstreamBody = {data: {task_result: {videos: [{duration: "5"}]}}};

    await v.triggerBillingVerdict({}, false, {upstreamBody, taskStatus: "completed"});

    expect(calls[0].payload.upstreamBody).toEqual(upstreamBody);
    expect(calls[0].payload.taskStatus).toBe("completed");
  });

  it("同步链路不传证据：两字段显式为 null（不是 undefined，后端按 null 判「没有」）", async () => {
    const {ctx, calls} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {billing: {totalCost: "1"}}});

    await v.triggerBillingVerdict({}, false);

    expect(calls[0].payload.upstreamBody).toBeNull();
    expect(calls[0].payload.taskStatus).toBeNull();
  });
});

describe("useBillingVerdict 被告提取", () => {
  it("无 knot_extend 字段时静默忽略（不抛、不置 skipped）", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({choices: []});
    expect(v.knotExtend.value).toBeNull();
    expect(v.upstreamUsage.value).toBeNull();
  });

  it("knot_extend 为数组或非对象时忽略", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: [1, 2]});
    expect(v.knotExtend.value).toBeNull();
    v.captureKnotExtend({knot_extend: "text"});
    expect(v.knotExtend.value).toBeNull();
  });

  it("提取被告的同时取并列的上游 usage", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {usage: {a: 1}}, usage: {prompt_tokens: 9}});
    expect(v.knotExtend.value).toEqual({usage: {a: 1}});
    expect(v.upstreamUsage.value).toEqual({prompt_tokens: 9});
  });

  it("并列 usage 缺失或非对象 → upstreamUsage 归 null", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {billing: {}}});
    expect(v.upstreamUsage.value).toBeNull();
    v.captureKnotExtend({knot_extend: {billing: {}}, usage: "not-object"});
    expect(v.upstreamUsage.value).toBeNull();
  });

  it("流式：knot_extend 与 [DONE] 共处一个事件时仍能提取", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtendFromStreamData('data: {"knot_extend":{"billing":{}}}\n\ndata: [DONE]\n\n');
    expect(v.knotExtend.value).toEqual({billing: {}});
  });

  it("流式：整包解析失败时逐行兜底", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    // 首行非 JSON（整包解析失败），第二行才是被告事件
    v.captureKnotExtendFromStreamData('data: not-json\n\ndata: {"knot_extend":{"usage":{}}}\n\n');
    expect(v.knotExtend.value).toEqual({usage: {}});
  });

  it("流式：全程无被告则保持 null", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtendFromStreamData('data: {"choices":[{"delta":{"content":"hi"}}]}\n\n');
    expect(v.knotExtend.value).toBeNull();
  });
});

describe("useBillingVerdict 承接模型定位", () => {
  it("优先响应头 x-knot-model", () => {
    const {ctx} = makeCtx();
    ctx.responseHeaders.value = {"x-knot-model": "gpt-4o-mini"};
    expect(useBillingVerdict(ctx).servingModelCode()).toBe("gpt-4o-mini");
  });

  it("响应头缺失时回退当前目标码", () => {
    const {ctx} = makeCtx();
    expect(useBillingVerdict(ctx).servingModelCode()).toBe("gpt-4o");
  });

  it("响应头为空白串时按缺失处理", () => {
    const {ctx} = makeCtx();
    ctx.responseHeaders.value = {"x-knot-model": "   "};
    expect(useBillingVerdict(ctx).servingModelCode()).toBe("gpt-4o");
  });
});

describe("useBillingVerdict 轮次令牌（防串台）", () => {
  it("请求在途期间重置 → 过期结果不写入", async () => {
    let release: (r: RoutingBillingVerdictResult) => void = () => {};
    const gate = new Promise<RoutingBillingVerdictResult>((resolve) => {
      release = resolve;
    });
    const {ctx} = makeCtx({requestVerdict: () => gate});
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}});

    const pending = v.triggerBillingVerdict({}, false);
    expect(v.verdictLoading.value).toBe(true);

    // 期间发起了新测试：令牌自增，上轮结果必须作废
    v.resetVerdictState();
    release({passed: false} as unknown as RoutingBillingVerdictResult);
    await pending;

    expect(v.verdictResult.value).toBeNull();
    expect(v.verdictLoading.value).toBe(false);
    expect(v.verdictSkipped.value).toBe(false);
  });

  it("请求在途期间重置 → 失败也不写入 error", async () => {
    let reject: (e: unknown) => void = () => {};
    const gate = new Promise<RoutingBillingVerdictResult>((_, rj) => {
      reject = rj;
    });
    const {ctx} = makeCtx({requestVerdict: () => gate});
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}});

    const pending = v.triggerBillingVerdict({}, false);
    v.resetVerdictState();
    reject(new Error("boom"));
    await pending;

    expect(v.verdictError.value).toBe("");
    expect(v.verdictLoading.value).toBe(false);
  });

  it("重置后所有裁判态归零", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}, usage: {a: 1}});
    v.verdictSkipped.value = true;
    v.verdictError.value = "x";
    v.resetVerdictState();
    expect(v.knotExtend.value).toBeNull();
    expect(v.upstreamUsage.value).toBeNull();
    expect(v.verdictSkipped.value).toBe(false);
    expect(v.verdictError.value).toBe("");
    expect(v.verdictResult.value).toBeNull();
  });
});

describe("useBillingVerdict 失败降级", () => {
  it("取后端 message（response.data.message 优先）", async () => {
    const {ctx} = makeCtx({
      requestVerdict: () => Promise.reject({response: {data: {message: "后端说不行"}}})
    });
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}});
    await v.triggerBillingVerdict({}, false);
    expect(v.verdictError.value).toBe("后端说不行");
    expect(v.verdictLoading.value).toBe(false);
  });

  it("无后端 message 时回退 error.message", async () => {
    const {ctx} = makeCtx({requestVerdict: () => Promise.reject(new Error("网络断了"))});
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}});
    await v.triggerBillingVerdict({}, false);
    expect(v.verdictError.value).toBe("网络断了");
  });

  it("完全无法取 message 时用默认文案", async () => {
    const {ctx} = makeCtx({requestVerdict: () => Promise.reject({})});
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}});
    await v.triggerBillingVerdict({}, false);
    expect(v.verdictError.value).toBe("计费校验请求失败");
  });
});

describe("useBillingVerdict 面板可见性", () => {
  it("任一裁判态激活即 active（skipped / error 也是设计内展示态）", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    expect(v.verdictActive.value).toBe(false);
    v.verdictSkipped.value = true;
    expect(v.verdictActive.value).toBe(true);
    v.verdictSkipped.value = false;
    v.verdictError.value = "x";
    expect(v.verdictActive.value).toBe(true);
    v.verdictError.value = "";
    expect(v.verdictActive.value).toBe(false);
  });

  it("loading 期间 active，结束后回落", () => {
    const {ctx} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.verdictLoading.value = true;
    expect(v.verdictActive.value).toBe(true);
    v.verdictLoading.value = false;
    expect(v.verdictActive.value).toBe(false);
  });
});

describe("useBillingVerdict 请求载荷", () => {
  it("upstreamUsage 缺失时传空对象而非 null", async () => {
    const {ctx, calls} = makeCtx();
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}});
    await v.triggerBillingVerdict({a: 1}, false);
    expect(calls[0].payload.upstreamUsage).toEqual({});
  });

  it("protocol 为空串时传 null", async () => {
    const {ctx, calls} = makeCtx({protocol: () => ""});
    const v = useBillingVerdict(ctx);
    v.captureKnotExtend({knot_extend: {}});
    await v.triggerBillingVerdict({}, false);
    expect(calls[0].payload.protocol).toBeNull();
  });
});
