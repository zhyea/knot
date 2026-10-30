import {computed, ref} from "vue";
import type {Dict} from "@/types";
import type {AsyncTaskAcceptedHandle} from "@/utils/routingTestPayload";

/** 首查延迟：刚提交的任务几乎不可能已经完成，立刻查一次只是白跑一趟上游 */
const FIRST_DELAY_MS = 1_500;
/** 退避基数：第二次起 3s */
const BASE_DELAY_MS = 3_000;
/** 退避系数：1.5（3s → 4.5s → 6.75s → …） */
const BACKOFF_FACTOR = 1.5;
/** 退避上限：再长就变成「用户盯着一个不动的界面」 */
const MAX_DELAY_MS = 15_000;
/** 本地观察上限：到点停止轮询，但<b>任务本身不被取消</b>（它还在网关侧跑） */
const LOCAL_TIMEOUT_MS = 600_000;
/** 「已用时」刷新间隔 */
const ELAPSED_INTERVAL_MS = 1_000;

/** 对外状态词（与网关 `AsyncTaskViewSupport` 同词表，不建码表） */
const STATUS_COMPLETED = "completed";
const STATUS_FAILED = "failed";

/** 一次查询在时间线上的一格 */
export interface AsyncTaskTimelineItem {
  /** 本次查询的时刻（毫秒时间戳） */
  at: number;
  /** 本次拿到的对外状态词 */
  status: string;
  /** 从提交到本次查询的耗时 */
  elapsedMs: number;
  /** 本次查询自身的失败原因（网络 / 5xx），与任务失败是两件事 */
  error?: string;
}

/** 终态回调载荷：抽屉据此提取 `knot_extend` / `upstream_body` 并触发计费校验 */
export interface AsyncTaskTerminalPayload {
  /** 对外状态词，仅 `completed` / `failed` 会走到这里 */
  status: string;
  /** 终态响应体整包（已解析） */
  body: Dict;
}

export interface AsyncTaskPollingContext {
  /** 组装请求头（复用抽屉的 Headers 表格，含测试头） */
  buildHeaders: () => Record<string, string>;
  /** 发起一次查询。注入而非直接 import：本 composable 必须能脱离 HTTP 单测 */
  queryTask: (
    url: string,
    headers: Record<string, string>,
    signal: AbortSignal
  ) => Promise<{status: number; data: unknown}>;
  /** 终态处理（由抽屉注入：提取证据 + 触发裁判） */
  onTerminal: (payload: AsyncTaskTerminalPayload) => void;
}

function asDict(value: unknown): Dict {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Dict) : {};
}

/**
 * 异步任务的轮询状态机：只管「什么时候查、查到的东西怎么记」，<b>不管业务</b>。
 *
 * <p>与 {@link useStreamBuffer} 同一纪律：发请求的<b>内容</b>（URL / 请求头）、终态之后做什么
 * （提取结算账、触发计费裁判）都留在抽屉。这里只做三件事：按退避排下一拍、把每次响应记进
 * {@link timeline}、在终态时把响应体交出去。</p>
 *
 * <h3>为什么要有令牌</h3>
 * <p>连续点两次「发送」、或关掉抽屉后重新打开，都会留下一条在途查询。它回来时如果不加判别，
 * 就会把<b>上一轮</b>的状态写进这一轮的界面（典型症状：新任务刚 queued，界面却已经 completed
 * 并带着上一轮的产物）。因此每次 {@link start} / {@link disposeAsyncPolling} 都自增令牌，
 * 回调里先比对令牌再写状态——同 `useBillingVerdict` 的 `verdictToken`。</p>
 *
 * <h3>为什么不建状态码表</h3>
 * <p>网关对外只给 `queued / processing / completed / failed / unknown` 五个词（库里的 0~5 码
 * 不对外）。这里直接拿对外词做判断与展示，再造一份映射就是把 {@code AsyncTaskViewSupport}
 * 抄第二遍，两份口径迟早会漂。</p>
 */
export function useAsyncTaskPolling(ctx: AsyncTaskPollingContext) {
  /** 本次轮询的任务句柄；null = 没有在跟踪的任务 */
  const taskCode = ref<string | null>(null);
  /** 最近一次拿到的对外状态词 */
  const taskStatus = ref("");
  /** 本次调试发起的查询次数（不是服务端的 attempts，别冒充） */
  const pollCount = ref(0);
  /** 从提交时刻起算的已用时 */
  const elapsedMs = ref(0);
  const timeline = ref<AsyncTaskTimelineItem[]>([]);
  /** 终态产物（`result_url` / `result_urls`…） */
  const result = ref<Dict | null>(null);
  /** 终态失败信息 `{code, message}` */
  const error = ref<Dict | null>(null);
  /** 本地观察上限已到：任务可能还在跑，我们只是不再看了 */
  const localTimeout = ref(false);
  /** 用户主动停止 */
  const stopped = ref(false);
  /** 是否仍在轮询（终态 / 停止 / 超时后为 false） */
  const polling = ref(false);
  /** 最近一次查询自身的失败原因（可重试错误，不停） */
  const lastError = ref("");

  let queryUrl: string | null = null;
  let startedAt = 0;
  let pollToken = 0;
  let delayTimer: ReturnType<typeof setTimeout> | null = null;
  let elapsedTimer: ReturnType<typeof setInterval> | null = null;
  let inFlight: AbortController | null = null;

  const elapsedLabel = computed(() => `${(elapsedMs.value / 1000).toFixed(1)}s`);

  /** 已完成的查询次数 → 下一拍延迟（首查 1.5s，之后 3s 起 ×1.5，cap 15s） */
  function nextDelayMs(polls: number): number {
    if (polls <= 0) {
      return FIRST_DELAY_MS;
    }
    const delay = BASE_DELAY_MS * Math.pow(BACKOFF_FACTOR, polls - 1);
    return Math.min(Math.round(delay), MAX_DELAY_MS);
  }

  function clearTimers(): void {
    if (delayTimer) {
      clearTimeout(delayTimer);
      delayTimer = null;
    }
    if (elapsedTimer) {
      clearInterval(elapsedTimer);
      elapsedTimer = null;
    }
  }

  /** 释放：停定时器 + 中止在途查询 + 令牌作废（晚到的响应一律丢弃） */
  function disposeAsyncPolling(): void {
    pollToken += 1;
    clearTimers();
    if (inFlight) {
      inFlight.abort();
      inFlight = null;
    }
    polling.value = false;
  }

  /** 复位展示态（新任务前调用；不碰任务句柄以外的东西） */
  function resetAsyncState(): void {
    taskCode.value = null;
    taskStatus.value = "";
    pollCount.value = 0;
    elapsedMs.value = 0;
    timeline.value = [];
    result.value = null;
    error.value = null;
    localTimeout.value = false;
    stopped.value = false;
    lastError.value = "";
  }

  function schedule(delayMs: number): void {
    if (delayTimer) {
      clearTimeout(delayTimer);
    }
    delayTimer = setTimeout(() => {
      delayTimer = null;
      void pollOnce();
    }, delayMs);
  }

  /** 用户点「立即查询」：不等下一拍，马上查一次 */
  function pollNow(): void {
    if (!polling.value) {
      return;
    }
    if (delayTimer) {
      clearTimeout(delayTimer);
      delayTimer = null;
    }
    void pollOnce();
  }

  /** 用户点「停止」：不再查询，但已拿到的内容保留 */
  function stop(): void {
    if (!polling.value) {
      return;
    }
    stopped.value = true;
    disposeAsyncPolling();
  }

  function finishTerminal(status: string, body: Dict, token: number): void {
    if (token !== pollToken) {
      return;
    }
    taskStatus.value = status;
    if (status === STATUS_COMPLETED) {
      result.value = asDict(body.result);
    } else {
      error.value = asDict(body.error);
    }
    disposeAsyncPolling();
    // 回调在状态收敛之后：抽屉从这里开始提取证据、跑计费校验
    ctx.onTerminal({status, body});
  }

  async function pollOnce(): Promise<void> {
    const url = queryUrl;
    if (!url || !polling.value) {
      return;
    }
    const token = pollToken;
    if (Date.now() - startedAt >= LOCAL_TIMEOUT_MS) {
      localTimeout.value = true;
      disposeAsyncPolling();
      return;
    }

    pollCount.value += 1;
    const controller = new AbortController();
    inFlight = controller;
    try {
      const response = await ctx.queryTask(url, ctx.buildHeaders(), controller.signal);
      inFlight = null;
      if (token !== pollToken) {
        return;
      }
      const body = asDict(response.data);
      const status = typeof body.status === "string" && body.status ? body.status : "unknown";
      taskStatus.value = status;
      timeline.value = [...timeline.value, {
        at: Date.now(),
        status,
        elapsedMs: Date.now() - startedAt
      }];

      if (status === STATUS_COMPLETED || status === STATUS_FAILED) {
        finishTerminal(status, body, token);
        return;
      }
      lastError.value = "";
      schedule(nextDelayMs(pollCount.value));
    } catch (e) {
      inFlight = null;
      if (token !== pollToken) {
        return;
      }
      const err = e as Dict & {response?: {status?: number; data?: Dict}};
      const httpStatus = err?.response?.status;
      // 句柄不存在 / 凭据失效：再查一万次也是同一个答案，立刻停并说清原因
      if (httpStatus === 404 || httpStatus === 401 || httpStatus === 403) {
        lastError.value = httpStatus === 404
          ? "查询地址返回 404：任务句柄不存在或已过期（保留窗口已过）"
          : "查询被拒绝（HTTP " + httpStatus + "）：检查消费者密钥与 Rule 头";
        timeline.value = [...timeline.value, {
          at: Date.now(),
          status: "unknown",
          elapsedMs: Date.now() - startedAt,
          error: lastError.value
        }];
        disposeAsyncPolling();
        return;
      }
      // 网络抖动 / 5xx / 用户中止：前者按退避继续（任务状态没变，只是我们没问上），
      // 后者说明这一轮已经作废（令牌也已自增，走不到这里）
      lastError.value = String(err?.response?.data?.message || (e as Error)?.message || "查询失败");
      timeline.value = [...timeline.value, {
        at: Date.now(),
        status: taskStatus.value || "unknown",
        elapsedMs: Date.now() - startedAt,
        error: lastError.value
      }];
      schedule(nextDelayMs(pollCount.value));
    }
  }

  /**
   * 开始轮询一个刚被接受的任务。
   *
   * <p>先 {@link disposeAsyncPolling} 再起：同一抽屉里连发两次时，上一轮必须彻底作废，
   * 否则两条链会同时往后写（一条查旧句柄、一条查新句柄），界面状态取决于谁后回来。</p>
   */
  function start(handle: AsyncTaskAcceptedHandle): void {
    disposeAsyncPolling();
    resetAsyncState();
    queryUrl = handle.queryUrl;
    taskCode.value = handle.taskCode;
    taskStatus.value = "queued";
    startedAt = Date.now();
    polling.value = true;
    elapsedMs.value = 0;
    elapsedTimer = setInterval(() => {
      elapsedMs.value = Date.now() - startedAt;
    }, ELAPSED_INTERVAL_MS);
    schedule(nextDelayMs(0));
  }

  return {
    taskCode,
    taskStatus,
    pollCount,
    elapsedMs,
    elapsedLabel,
    timeline,
    result,
    error,
    localTimeout,
    stopped,
    polling,
    lastError,
    nextDelayMs,
    start,
    stop,
    pollNow,
    resetAsyncState,
    disposeAsyncPolling
  };
}
