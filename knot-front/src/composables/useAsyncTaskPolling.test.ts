import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {useAsyncTaskPolling, type AsyncTaskPollingContext} from "@/composables/useAsyncTaskPolling";
import type {Dict} from "@/types";

/**
 * 异步任务轮询的六条硬约束（每一条失守都是「界面上看到的东西不是真的」）：
 *
 * ① 202 → 退避 → 终态，节拍按 1.5s / 3s / 4.5s… cap 15s；
 * ② 终态后不再发请求（否则用户关掉抽屉前会被一直查）；
 * ③ `dispose` 之后晚到的响应被令牌丢弃（否则上一轮结果写进这一轮）；
 * ④ 404 / 401 / 403 立刻停且给出可执行原因（再查一万次也是同一个答案）；
 * ⑤ 网络 / 5xx 不停，继续退避（任务状态没变，只是我们没问上）；
 * ⑥ 本地上限到点停止，任务本身不被取消。
 *
 * 用假定时器：这几条全是「时间到了会怎样」，真等就是靠运气。
 */

function makeCtx(overrides: Partial<AsyncTaskPollingContext> = {}) {
  const calls: Array<{url: string; headers: Record<string, string>}> = [];
  const terminals: Array<{status: string; body: Dict}> = [];
  const ctx: AsyncTaskPollingContext = {
    buildHeaders: () => ({"X-Knot-Test-Return-Usage": "true"}),
    queryTask: async (url, headers) => {
      calls.push({url, headers});
      return {status: 200, data: {status: "processing"}};
    },
    onTerminal: (payload) => terminals.push({status: payload.status, body: payload.body}),
    ...overrides
  };
  return {ctx, calls, terminals};
}

const HANDLE = {taskCode: "tc-1", queryUrl: "/openai/v1/tasks/tc-1"};

describe("useAsyncTaskPolling 节拍与终态", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("首查 1.5s，拿到非终态后按 3s 起 ×1.5 退避，终态后停止", async () => {
    const responses = [
      {status: 200, data: {status: "processing"}},
      {status: 200, data: {status: "processing"}},
      {status: 200, data: {status: "completed", result: {result_url: "https://x/a.mp4"}}}
    ];
    let index = 0;
    const {ctx, calls, terminals} = makeCtx({
      queryTask: async (url, headers) => {
        calls.push({url, headers});
        return responses[Math.min(index++, responses.length - 1)];
      }
    });
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    expect(p.taskStatus.value).toBe("queued");
    expect(p.polling.value).toBe(true);

    // 首查：1.5s
    expect(calls).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(1_499);
    expect(calls).toHaveLength(0);
    await vi.advanceTimersByTimeAsync(1);
    expect(calls).toHaveLength(1);
    expect(p.pollCount.value).toBe(1);
    expect(p.taskStatus.value).toBe("processing");

    // 第二次：3s（3s × 1.5^0）
    await vi.advanceTimersByTimeAsync(2_999);
    expect(calls).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(calls).toHaveLength(2);

    // 第三次：4.5s（3s × 1.5^1）
    await vi.advanceTimersByTimeAsync(4_500);
    expect(calls).toHaveLength(3);

    expect(p.taskStatus.value).toBe("completed");
    expect(p.polling.value).toBe(false);
    expect(p.result.value).toEqual({result_url: "https://x/a.mp4"});
    expect(terminals).toEqual([{
      status: "completed",
      body: {status: "completed", result: {result_url: "https://x/a.mp4"}}
    }]);

    // 终态之后一拍都不许再发
    await vi.advanceTimersByTimeAsync(60_000);
    expect(calls).toHaveLength(3);
  });

  it("退避有上限：第 N 拍不会超过 15s", () => {
    const {ctx} = makeCtx();
    const p = useAsyncTaskPolling(ctx);
    expect(p.nextDelayMs(0)).toBe(1_500);
    expect(p.nextDelayMs(1)).toBe(3_000);
    expect(p.nextDelayMs(2)).toBe(4_500);
    expect(p.nextDelayMs(3)).toBe(6_750);
    expect(p.nextDelayMs(20)).toBe(15_000);
  });

  it("终态 failed：把 error 记下来并回调，产物为空", async () => {
    const {ctx, terminals} = makeCtx({
      queryTask: async () => ({
        status: 200,
        data: {status: "failed", error: {code: "UPSTREAM_TASK_FAILED", message: "prompt rejected"}}
      })
    });
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);

    expect(p.taskStatus.value).toBe("failed");
    expect(p.error.value).toEqual({code: "UPSTREAM_TASK_FAILED", message: "prompt rejected"});
    expect(p.result.value).toBeNull();
    expect(terminals[0].status).toBe("failed");
  });

  it("时间线逐条累积：每条带状态与已用时", async () => {
    const {ctx} = makeCtx();
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);
    await vi.advanceTimersByTimeAsync(3_000);

    expect(p.timeline.value).toHaveLength(2);
    expect(p.timeline.value[0].status).toBe("processing");
    expect(p.timeline.value[0].elapsedMs).toBeGreaterThanOrEqual(1_500);
    expect(p.timeline.value[1].elapsedMs).toBeGreaterThan(p.timeline.value[0].elapsedMs);
  });
});

describe("useAsyncTaskPolling 停止与丢弃", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("用户停止：不再请求，但已拿到的内容保留", async () => {
    const {ctx, calls} = makeCtx();
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);
    expect(calls).toHaveLength(1);

    p.stop();
    expect(p.stopped.value).toBe(true);
    expect(p.polling.value).toBe(false);
    // 时间线保留：停止不等于「什么都没发生」
    expect(p.timeline.value).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(60_000);
    expect(calls).toHaveLength(1);
  });

  it("⭐ dispose 之后晚到的响应被令牌丢弃（不许写进新一轮）", async () => {
    let resolveFirst: ((value: {status: number; data: unknown}) => void) | null = null;
    const {ctx, terminals} = makeCtx({
      queryTask: () => new Promise((resolve) => {
        resolveFirst = resolve;
      })
    });
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);

    // 在途期间关抽屉：令牌作废
    p.disposeAsyncPolling();

    // 晚到的终态响应必须被丢掉
    resolveFirst!({status: 200, data: {status: "completed", result: {result_url: "https://x/a.mp4"}}});
    await vi.advanceTimersByTimeAsync(0);

    expect(p.taskStatus.value).toBe("queued");
    expect(p.result.value).toBeNull();
    expect(terminals).toHaveLength(0);
  });

  it("重复 start：上一轮彻底作废，只有一个链在跑", async () => {
    const {ctx, calls} = makeCtx();
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    p.start({taskCode: "tc-2", queryUrl: "/openai/v1/tasks/tc-2"});
    await vi.advanceTimersByTimeAsync(1_500);

    expect(calls).toHaveLength(1);
    expect(calls[0].url).toBe("/openai/v1/tasks/tc-2");
    expect(p.taskCode.value).toBe("tc-2");
    expect(p.pollCount.value).toBe(1);
  });

  it("「立即查询」不等下一拍，马上打一次", async () => {
    const {ctx, calls} = makeCtx();
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);
    expect(calls).toHaveLength(1);

    p.pollNow();
    await vi.advanceTimersByTimeAsync(0);
    expect(calls).toHaveLength(2);

    // 立即查询之后仍按退避继续（pollCount=2 → 4.5s），不是回到首查 1.5s
    await vi.advanceTimersByTimeAsync(3_000);
    expect(calls).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1_500);
    expect(calls).toHaveLength(3);
  });
});

describe("useAsyncTaskPolling 错误分类", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("404 立刻停并给出「句柄不存在」的原因", async () => {
    const {ctx, calls} = makeCtx({
      queryTask: async (url, headers) => {
        calls.push({url, headers});
        throw {response: {status: 404, data: {message: "task not found"}}};
      }
    });
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);

    expect(p.polling.value).toBe(false);
    expect(p.lastError.value).toContain("404");
    expect(p.lastError.value).toContain("句柄");
    // 时间线上也要留一条：否则界面只有一句错误，看不出「查过几次才失败」
    expect(p.timeline.value).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(60_000);
    expect(calls).toHaveLength(1);
  });

  it("401 / 403 同样立刻停，但原因是凭据/规则头", async () => {
    const {ctx, calls} = makeCtx({
      queryTask: async (url, headers) => {
        calls.push({url, headers});
        throw {response: {status: 401, data: {message: "invalid key"}}};
      }
    });
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);

    expect(p.polling.value).toBe(false);
    expect(p.lastError.value).toContain("401");
    expect(p.lastError.value).toContain("密钥");
    await vi.advanceTimersByTimeAsync(60_000);
    expect(calls).toHaveLength(1);
  });

  it("⭐ 5xx / 网络错误不停：任务状态没变，只是我们没问上", async () => {
    let failed = false;
    const {ctx, calls} = makeCtx({
      queryTask: async (url, headers) => {
        calls.push({url, headers});
        if (!failed) {
          failed = true;
          throw {response: {status: 502, data: {message: "bad gateway"}}};
        }
        return {status: 200, data: {status: "processing"}};
      }
    });
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    await vi.advanceTimersByTimeAsync(1_500);
    expect(p.polling.value).toBe(true);
    expect(p.lastError.value).toBe("bad gateway");

    // 退避后继续（这一拍成功 → 错误清空）
    await vi.advanceTimersByTimeAsync(3_000);
    expect(calls).toHaveLength(2);
    expect(p.lastError.value).toBe("");
    expect(p.polling.value).toBe(true);
  });

  it("本地上限到点停止：置 localTimeout，任务本身不被取消", async () => {
    const {ctx, calls} = makeCtx();
    const p = useAsyncTaskPolling(ctx);

    p.start(HANDLE);
    // 一路推进到超过 10min（每拍最多 15s，600 秒后必然到点）
    await vi.advanceTimersByTimeAsync(700_000);

    expect(p.localTimeout.value).toBe(true);
    expect(p.polling.value).toBe(false);
    const settled = calls.length;
    await vi.advanceTimersByTimeAsync(60_000);
    expect(calls).toHaveLength(settled);
  });
});
