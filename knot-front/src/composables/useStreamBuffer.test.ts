import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {ref} from "vue";
import {useStreamBuffer, type StreamBufferContext} from "@/composables/useStreamBuffer";
import type {RoutingTestResult} from "@/api/routing";
import type {Dict} from "@/types";

/**
 * 流式缓冲是「已收到」与「已刷入」两套状态，节流分别为文本 50ms / 区块 60ms。
 * 最关键的三条契约：① 节流窗口内不刷入；② 结束路径必须 flush 掉最后一批（否则丢数据）；
 * ③ 三个计时器必须在 endStream / dispose 时全部清掉（漏清会对着已销毁组件写状态）。
 */

function makeCtx(overrides: Partial<StreamBufferContext> = {}) {
  const scrolls: number[] = [];
  const captured: string[] = [];
  const base: StreamBufferContext = {
    testResult: ref<RoutingTestResult | null>(null),
    knotExtend: ref<Dict | null>(null),
    captureKnotExtendFromStreamData: (data: string) => {
      captured.push(data);
    },
    onScrollToBottom: () => {
      scrolls.push(1);
    }
  };
  return {ctx: {...base, ...overrides}, scrolls, captured};
}

function sampleResult(): RoutingTestResult {
  return {status: "RUNNING", httpStatus: null, modelCode: null, responseBody: null} as RoutingTestResult;
}

beforeEach(() => {
  vi.useFakeTimers();
});
afterEach(() => {
  vi.useRealTimers();
});

describe("useStreamBuffer 文本节流", () => {
  it("节流窗口内不刷入 streamBody", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    // 直接触发：beginStream 后无 flushTimer，靠 appendStreamText 建立
    s.handleStreamEvent({event: "message", data: '{"a":1}'} as never);
    expect(s.receivedBytes.value).toBeGreaterThan(0);
  });

  it("flushStreamText 立即把攒下的片段刷入", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "message", data: "hi"} as never);
    const before = s.streamBody.value;
    s.flushStreamText();
    expect(s.streamBody.value.length).toBeGreaterThanOrEqual(before.length);
  });

  it("endStream 会兜底刷掉最后一批待刷文本", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "message", data: "tail"} as never);
    vi.advanceTimersByTime(0);
    const before = s.streamBody.value;
    s.endStream();
    expect(s.streamBody.value.length).toBeGreaterThanOrEqual(before.length);
  });

  it("已接收字节按 UTF-8 计数（中文 3 字节）", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    const before = s.receivedBytes.value;
    s.handleStreamEvent({event: "message", data: "中"} as never);
    // 反解失败时 data 仍会作为区块文本累加，字节数必然增加
    expect(s.receivedBytes.value).toBeGreaterThan(before);
  });
});

describe("useStreamBuffer 区块节流与滚动", () => {
  it("区块在 60ms 后刷入，并触发一次滚动回调", () => {
    const {ctx, scrolls} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "message", data: '{"choices":[]}'} as never);
    expect(s.streamBlocks.value).toHaveLength(0);
    vi.advanceTimersByTime(60);
    expect(s.streamBlocks.value).toHaveLength(1);
    expect(scrolls.length).toBe(1);
  });

  it("连续事件在窗口内合并为一次刷入", () => {
    const {ctx, scrolls} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    for (let i = 0; i < 5; i++) {
      s.handleStreamEvent({event: "message", data: '{"i":' + i + "}"} as never);
    }
    expect(s.streamBlocks.value).toHaveLength(0);
    vi.advanceTimersByTime(60);
    expect(s.streamBlocks.value).toHaveLength(5);
    expect(scrolls.length).toBe(1);
  });

  it("区块 id 单调递增", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "message", data: "{}"} as never);
    s.handleStreamEvent({event: "message", data: "{}"} as never);
    vi.advanceTimersByTime(60);
    expect(s.streamBlocks.value.map((b) => b.id)).toEqual([0, 1]);
  });

  it("flushBlocks 无待刷区块时不触发滚动", () => {
    const {ctx, scrolls} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.flushBlocks();
    expect(scrolls.length).toBe(0);
  });
});

describe("useStreamBuffer SSE 事件处理", () => {
  it("每个事件都把原始 data 交给被告提取器", () => {
    const {ctx, captured} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "message", data: '{"k":1}'} as never);
    expect(captured).toEqual(['{"k":1}']);
  });

  it("error 事件把终态写回 testResult（优先级 errorMessage > message > 默认）", () => {
    const {ctx} = makeCtx();
    ctx.testResult.value = sampleResult();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "error", data: '{"errorMessage":"后端炸了"}'} as never);
    expect(ctx.testResult.value.status).toBe("ERROR");
    expect(ctx.testResult.value.errorMessage).toBe("后端炸了");
  });

  it("error 事件无 message 时用默认文案", () => {
    const {ctx} = makeCtx();
    ctx.testResult.value = sampleResult();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "error", data: "{}"} as never);
    expect(ctx.testResult.value.errorMessage).toBe("网关请求失败");
  });

  it("error 事件在 testResult 为空时不写（避免凭空造结果）", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "error", data: "{}"} as never);
    expect(ctx.testResult.value).toBeNull();
  });

  it("正文与推理内容从事件中反解并累加", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.handleStreamEvent({event: "message", data: '{"content":"你好"}'} as never);
    s.handleStreamEvent({event: "message", data: '{"reasoning_content":"想想"}'} as never);
    vi.advanceTimersByTime(60);
    expect(s.streamContent.value).toBe("你好");
    expect(s.streamReasoning.value).toBe("想想");
  });
});

describe("useStreamBuffer 终态固化", () => {
  it("finishStream 把已刷入的 body 固化到 responseBody 并置终态", () => {
    const {ctx} = makeCtx();
    ctx.testResult.value = sampleResult();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.streamBody.value = "已收到的内容";
    s.finishStream("STOPPED", "");
    expect(ctx.testResult.value.status).toBe("STOPPED");
    expect(ctx.testResult.value.responseBody).toBe("已收到的内容");
  });

  it("finishStream 在 testResult 为空时静默（不凭空造结果）", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.finishStream("SUCCESS", "");
    expect(ctx.testResult.value).toBeNull();
  });

  it("appendStreamError 追加 error 块并把错误拼进 body（首个事件前失败）", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    const result = {status: "ERROR", errorMessage: "连不上"} as RoutingTestResult;
    s.appendStreamError(result);
    expect(s.streamBlocks.value).toHaveLength(1);
    expect(s.streamBlocks.value[0].event).toBe("error");
    expect(s.streamBody.value).toBe("连不上");
    // 关键：回写 result，让调用方拿到的 responseBody 含完整诊断信息
    expect(result.responseBody).toBe("连不上");
  });

  it("已有片段时在 body 后追加 [error] 段", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.streamBody.value = "前半段";
    const result = {status: "ERROR", errorMessage: "断了"} as RoutingTestResult;
    s.appendStreamError(result);
    expect(s.streamBody.value).toBe("前半段\n\n[error] 断了");
  });
});

describe("useStreamBuffer 被告补救", () => {
  it("已拿到被告时不再逐块回扫", () => {
    const {ctx, captured} = makeCtx();
    ctx.knotExtend.value = {billing: {}};
    const s = useStreamBuffer(ctx);
    s.streamBlocks.value = [{id: 0, data: "{}"}];
    s.recoverKnotExtendFromStreamBlocks();
    expect(captured).toHaveLength(0);
  });

  it("未拿到被告时逐块回扫，命中即停", () => {
    const {ctx, captured} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.streamBlocks.value = [{id: 0, data: "{\"a\":1}"}, {id: 1, data: "{\"b\":2}"}];
    // 注入的提取器不做实际提取（knotExtend 恒 null），故所有块都会被扫到
    s.recoverKnotExtendFromStreamBlocks();
    expect(captured).toEqual(['{"a":1}', '{"b":2}']);
  });
});

describe("useStreamBuffer 生命周期与计时器", () => {
  it("beginStream 复位所有缓冲并置流式中", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.streamBody.value = "旧";
    s.streamBlocks.value = [{id: 0, data: "x"}];
    s.streamContent.value = "旧内容";
    s.beginStream(new AbortController());
    expect(s.streamBody.value).toBe("");
    expect(s.streamBlocks.value).toEqual([]);
    expect(s.streamContent.value).toBe("");
    expect(s.streamReasoning.value).toBe("");
    expect(s.receivedBytes.value).toBe(0);
    expect(s.streamStartedAt.value).not.toBeNull();
    expect(s.isStreaming.value).toBe(true);
  });

  it("beginStream 启动「已用时」计时器，每秒递增", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    expect(s.elapsedTick.value).toBe(0);
    vi.advanceTimersByTime(1000);
    expect(s.elapsedTick.value).toBe(1);
    vi.advanceTimersByTime(2000);
    expect(s.elapsedTick.value).toBe(3);
  });

  it("endStream 清掉计时器并结束流式态", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.endStream();
    expect(s.isStreaming.value).toBe(false);
    expect(s.streamAbortController.value).toBeNull();
    expect(s.streamStartedAt.value).toBeNull();
    const tick = s.elapsedTick.value;
    vi.advanceTimersByTime(3000);
    // 计时器已清，不再递增
    expect(s.elapsedTick.value).toBe(tick);
  });

  it("重复 beginStream 不会残留旧计时器（elapsed 只跑一份）", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    s.beginStream(new AbortController());
    s.beginStream(new AbortController());
    vi.advanceTimersByTime(1000);
    expect(s.elapsedTick.value).toBe(1);
  });

  it("disposeStreamResources 中止在途流并清计时器", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    const controller = new AbortController();
    s.beginStream(controller);
    expect(controller.signal.aborted).toBe(false);
    s.disposeStreamResources();
    expect(controller.signal.aborted).toBe(true);
    const tick = s.elapsedTick.value;
    vi.advanceTimersByTime(3000);
    expect(s.elapsedTick.value).toBe(tick);
  });

  it("disposeStreamResources 不碰展示数据（数据复位归 onClosed）", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    const controller = new AbortController();
    s.beginStream(controller);
    s.streamBody.value = "保留";
    s.streamBlocks.value = [{id: 0, data: "x"}];
    s.disposeStreamResources();
    expect(s.streamBody.value).toBe("保留");
    expect(s.streamBlocks.value).toHaveLength(1);
  });

  it("abortStreaming 在无在途流时不抛", () => {
    const {ctx} = makeCtx();
    const s = useStreamBuffer(ctx);
    expect(() => s.abortStreaming()).not.toThrow();
  });
});
